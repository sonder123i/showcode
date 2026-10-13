"""亮码视觉系统的棘轮。和 core/parse/corpus 那条识别棘轮同一个做法：
每档指标有一个**不许下调**的地板，改坏了当场就红，不靠人记住该数什么。

    python3 tools/visual-audit.py            # 打印四张表
    python3 tools/visual-audit.py --ci       # 低于地板就非零退出

CI 里跑的是 --ci（.github/workflows/ci.yml 的「视觉系统棘轮」那一步）。

现场模式（ui/onsite/）不在计数范围内：那一屏的契约是纯黑 on 纯白 + 三态，
它本来就该有自己的一套字号算法（CodeFontSize 按码长算），拿管理模式的档位去要求它是错的。
"""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'app', 'src', 'main')
ONSITE = os.sep + 'onsite' + os.sep

# ── 地板：只许变好，不许变差 ────────────────────────────────────
# 沿用 core/parse/corpus 那条识别棘轮的约定：地板 = 收口当天的实测值，之后只许更好。
# 收口前的真实基线（2026-10-13 量）：fontSize 裸值 126 处 / 14 档，
# 64 处字重里 Normal 只有 2 处（粗体占比 86%），圆角 10 档，手调文字透明度 77 处 / 18 个值。
FLOORS = {
    '管理模式 fontSize 裸值处数': (0, '≤'),
    '管理模式 字号档数': (0, '≤'),          # 裸值归零之后这一档就是 0，role 数另算
    '管理模式 字重档数': (3, '≤'),          # 400 / 700 / 800
    '管理模式 圆角裸值处数': (0, '≤'),
    '管理模式 手调文字透明度': (0, '≤'),
    '管理模式 粗体占比': (53, '≤%'),        # 基线 86；剩下的全是标题/动作/数字，正文已全部回 400
    '取件点类型有图标的比例': (100, '≥%'),
    '中性面档数': (4, '≥'),
}

KINDS = ['LOCKER', 'STATION', 'SELFPICK']


def kt_files():
    for dp, _, fs in os.walk(ROOT):
        for f in fs:
            if f.endswith('.kt'):
                yield os.path.join(dp, f)


def main():
    mgmt, onsite = [], []
    for p in kt_files():
        (onsite if ONSITE in p else mgmt).append((p, open(p, encoding='utf-8').read()))
    # token 声明文件自己就是字阶的定义处，不能算进「消费侧」的债
    THEME = os.path.join('dev', 'showcode', 'ui', 'theme') + os.sep
    body = ''.join(tx for tp, tx in mgmt if THEME not in tp)

    sizes = [int(x) for x in re.findall(r'fontSize = (\d+)\.sp', body)]
    weights = re.findall(r'FontWeight\.(\w+)', body)
    radii = re.findall(r'RoundedCornerShape\((\d+(?:\.\d+)?)\.dp\)', body)
    # 只有文字色里的 alpha 算债；语义遮罩（水印、tint 底）另有声明值
    text_alpha = re.findall(r'(?:OnsiteInk|Ink|InkMuted|InkSecondary|Color\.Black)\.copy\(alpha', body)
    # 粗体占比必须连 role 一起算：迁移之后字重搬进了 TypeScale.kt，
    # 只数调用点的 FontWeight. 字面量会把「全靠 role 撑着粗体」误报成「已经收敛了」。
    ts_path = os.path.join(ROOT, 'java', 'dev', 'showcode', 'ui', 'theme', 'TypeScale.kt')
    role_weight = dict(re.findall(r'val (\w+) = TextStyle\([^)]*?fontWeight = FontWeight\.(\w+)',
                                   open(ts_path, encoding='utf-8').read(), re.S))
    role_uses = re.findall(r'ShowCodeText\.(\w+)', body)
    bold = sum(1 for w in weights if w in ('Bold', 'ExtraBold', 'SemiBold'))
    bold += sum(1 for r in role_uses if role_weight.get(r, '') in ('Bold', 'ExtraBold', 'SemiBold'))
    weights_all = list(weights) + [role_weight.get(r, 'Normal') for r in role_uses]
    total_weight_sites = len(weights_all)
    roles = len({m.group(1) for m in re.finditer(r'ShowCodeText\.(\w+)', body)})
    icons_drawn = [f for f in os.listdir(os.path.join(ROOT, 'res', 'drawable')) if f.startswith('ic_point_')]
    kind_covered = sum(1 for k in KINDS if any(k.lower() in f for f in icons_drawn))
    surfaces = len(re.findall(r'^val (Canvas|Surface\w+|Hairline\w*) = Color', open(
        os.path.join(ROOT, 'java', 'dev', 'showcode', 'ui', 'theme', 'Color.kt'), encoding='utf-8').read(), re.M))

    measured = {
        '管理模式 fontSize 裸值处数': len(sizes),
        '管理模式 字号档数': len(set(sizes)) if sizes else 0,
        '管理模式 字重档数': len(set(weights_all)),
        '管理模式 圆角裸值处数': len(radii),
        '管理模式 手调文字透明度': len(text_alpha),
        '管理模式 粗体占比': round(100.0 * bold / max(1, total_weight_sites)),
        '取件点类型有图标的比例': round(100.0 * kind_covered / len(KINDS)),
        '中性面档数': surfaces,
    }
    detail = {
        '字号档': sorted(set(sizes)),
        '字重档': sorted(set(weights)),
        '圆角档': sorted(set(radii)),
        'role 引用数': len(re.findall(r'ShowCodeText\.\w+', body)),
        'role 种数': roles,
        '现场模式(不计入)': sum(len(re.findall(r'fontSize = (\d+)\.sp', t)) for _, t in onsite),
    }

    ci = '--ci' in sys.argv
    print('%-26s %8s %8s  %s' % ('指标', '现在', '地板', '判定'))
    bad = []
    for name, (floor, op) in FLOORS.items():
        now = measured[name]
        ok = now <= floor if op.startswith('≤') else now >= floor
        if not ok:
            bad.append(name)
        print('%-26s %8s %6s%3s  %s' % (name, now, op, floor, 'OK' if ok else 'X 破了'))
    print()
    for k, v in detail.items():
        print('  %-18s %s' % (k, v))
    if bad:
        print('\n破了地板：%s' % '、'.join(bad))
        sys.exit(1)
    print('\n地板全部守住。')


main()
