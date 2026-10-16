# 一键查快递 - 剪贴板入口功能 PRD

## 背景与价值

用户从其他 App 复制了快递单号后，需要手动打开亮码 App → 找到手动录入 → 粘贴原文。这个流程太长，尤其是在柜机前等急用的时候。

**核心价值**：提供「粘过来就能用」的捷径——点一下底栏搜索旁边的放大镜图标，自动填入剪贴板内容，识别成功直接填表 + 跳转。

## AC（验收标准）

### AC1: 入口可见性
- 首页顶栏右上角有两个图标：**搜索框开关** + **查快递按钮**（仅当有件时显示）
- 查快递按钮图标为线框放大镜符号（6px stroke），颜色 InkSecondary
- 未取件时不显示该按钮

### AC2: 剪贴板自动填充
- 进入查快递页时，自动读取系统剪贴板
- 若有文本，填入「粘贴快递单号或取件短信原文」输入框
- 若无文本，保持空白让用户手输

### AC3: 智能提取
- 使用已有内置解析器尝试从文本中提取：取件码、取件点、柜号
- 提取成功则在表单下方展示「自动提取」结果区，绿色字体
- 若失败则红色提示：「原文里没认出来，请手动填下面两项」

### AC4: 保存逻辑
- 用户点击「保存并跳转」→ 调 `manualAdd()` 落库
- 成功 = 清空当前表单 + 回首页
- 失败 = 保留已填内容 + 红色错误提示

### AC5: 历史记录
- 每次保存成功的 raw 文本加入本地 `lookupHistory`（内存态）
- 去重后加到头部，最多保留 10 条
- 历史列表按时间倒序，点击可直接回填到输入框

### AC6: 返回键处理
- 查快递页 → 返回 = 清空预填 + 回到来源页（默认 HOME）
- 来源页可配置为 ONBOARDING/MANUAL_IMPORT 等

## 文件清单

### 新增文件
```
app/src/main/java/dev/showcode/ui/tools/
├── LookupTabScreen.kt          # 查快递主页组件
├── LookupResultCard.kt         # 结果卡片（可选）
└── RecentLookupList.kt         # 历史记录列表

app/src/main/res/drawable/
└── ic_lookup.xml               # 线框放大镜图标
```

### 修改文件
```
app/src/main/java/dev/showcode/ui/
├── ShowCodeRoot.kt            # 新增 ROUTE.LOOKUP + 路由切换逻辑
└── home/HomeScreen.kt         # 顶栏新增查快递按钮入口

app/src/main/res/values/
└── strings.xml                # 新增 10+ 条查快递相关文案

app/src/test/java/
└── dev/showcode/core/
    └── CourierParserTest.kt   # 快递单号格式识别测试（待补充）

docs/
├── help/pinduoduo-taken-code.md  # 帮助图（可选）
└── verification-checklist.md     # 补上 L7 验收项
README.md                         # 更新功能说明部分
```

## 技术细节

### 解析逻辑复用
- 完全复用已有的 `PickupCodeParser.parse()` 函数
- 入参：`IncomingMessage(body = clipboardText, sender = "", channel = Source.SMS)`
- 出参：`ParsedParcel{code, pointName, lockerNo, carrier}`
- 无需新增正则规则

### 数据存储策略
- 历史记录只存内存（`mutableStateListOf`），不持久化
- 理由：这是「临时工具」不是正式档案，10 条以内足够回溯
- 若 V2 需要持久化再引入 Room 表

### 边界情况处理
1. **剪贴板为空** → 显示空输入框
2. **剪贴板不是文本**（图片/二进制）→ `primaryText = null` → 同空值处理
3. **解析失败** → 留白等手填
4. **多条码在同一文本** → 取第一个成功提取的（已有解析器逻辑）
5. **重复条目** → `removeAll + add(0, item)` 去重保序

## 未来扩展（V2）

- [ ] 支持粘贴多个单号批量导入
- [ ] 增加快递公司选择（下拉框补全）
- [ ] 支持直接跳转淘宝/拼多多/菜鸟等官方 App 的查件页
- [ ] 增加快捷扫描摄像头入口（拍照 OCR）

---

**本版本范围锁定在「能跑通的 MVP」**：粘过来→识别→填表→保存→回首页。所有扩展需求都放到 V2。
