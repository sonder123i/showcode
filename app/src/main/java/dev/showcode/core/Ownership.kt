package dev.showcode.core

/**
 * 归属判定（story-7 最小版）。
 *
 * 降级后的范围是「先只做手动打标签」，但有一条自动信号便宜且可靠，值得先接上：
 * 妈妈把短信转发给儿子时，通知的来源应用就是微信或 QQ。**来源包名是系统给的，
 * 不是猜正文猜出来的**，所以它不违背「保守」这条戒律——猜错了代价是一个标签可改，
 * 而不是一个码不见了。
 */
fun ownerOfForward(fromPackage: String?): Owner =
    if (fromPackage != null && fromPackage in FORWARD_PACKAGES) Owner.FAMILY else Owner.SELF

/** 分组名默认值。AC2 要求这个名字用户能改，所以它只是起点，不是文案常量。 */
const val FAMILY_LABEL_DEFAULT = "帮妈妈取"

/** 只认这两个转发场景里真正会带正文进来的包。QQ 也常被家人用来转发截图/文本。 */
private val FORWARD_PACKAGES = setOf("com.tencent.mm", "com.tencent.mobileqq")
