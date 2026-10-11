package dev.showcode.core

import java.text.Normalizer

/**
 * 取件点归一化。frame.json → pickup_point_normalization 已锁定为「保守」：
 * **不自动合并任何名称不同的取件点**。宁可首页多一张卡片，也不让用户在现场找不到码——
 * 现场模式下「码不见了」是灾难性失败，「多了一张卡片」只是难看。
 *
 * 所以这里只归一「同一个名字的不同写法」：全角/半角、大小写、空格、标点。
 * 不做任何语义合并、不做地址推断、不做相似度匹配。
 * 「菜鸟驿站(东门店)」与「菜鸟驿站(阳光花园小区东门店)」因此会是两张卡片，
 * 这是已接受的代价；长期解是 V2 的别名映射库，短期解是 story-6 的可逆手动合并。
 */
fun pointKey(rawName: String): String = Normalizer
    .normalize(rawName, Normalizer.Form.NFKC)
    .lowercase()
    .filterNot { it.isWhitespace() || it in NAME_PUNCTUATION }

/**
 * NFKC 已经把全角括号、冒号、感叹号、逗号折成半角，所以这里只列半角形式，
 * 加上 NFKC 不会碰的中文句读（。、·・）。
 */
private val NAME_PUNCTUATION = setOf(
    '(', ')', '[', ']', '【', '】', '{', '}',
    '-', '_', '—', '·', '・', '.', '。', ',', '、', ':', ';', '!', '?', '/', '\\', '\'', '"',
)
