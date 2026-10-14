package dev.showcode.core

/**
 * 「打开那个官方 App」这件事的四种结局。
 *
 * 这一格不是装饰：竞品 issue #39 的标题就是「不能添加淘宝和拼多多身份码」，正文只有一句
 * **「点击时无反应」**——而维护者给的解法是一个系统开关（小米：应用设置→其他权限→链式启动管理）。
 * 也就是说**点了没反应是这条路径上最常见的一种失败**，而静默吞掉它，用户既不知道下一步，
 * 也永远不会想到要去找那个开关。竞品那位维护者是在 issue 里回答的；这句话应该在屏上。
 *
 * 所以这里把结局分成能各给一句话的四档，而不是一个布尔。
 */
sealed interface LaunchOutcome {
    /** 真的起来了。这一档不需要说话——屏幕已经交给对方了。 */
    data object Opened : LaunchOutcome

    /**
     * 查得到这个包、也解析得出启动入口，但 `startActivity` 没成。
     * 文案要说**那件唯一有用的事**：这类拦截在国产 ROM 上是一个系统开关，不是一个 bug。
     */
    data object Blocked : LaunchOutcome

    /** 启动入口都解不出来 —— 它没装，或者已经换成了另一个签名的包。 */
    data object NotInstalled : LaunchOutcome

    /** 这张码没选过官方 App，或者包名是空的——按钮本来就不该出现在这一屏。 */
    data object NoTarget : LaunchOutcome
}
