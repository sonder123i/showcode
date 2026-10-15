package dev.showcode.core

/**
 * 「推送盲区」的判定。
 *
 * 它要回答的问题只有一个：**这个用户的物流链路里，有没有平台根本不往手机推东西**。
 * 有些件不发短信、也不推通知，只在拼多多/淘宝自己的列表里露一下（真实案例：菜鸟驿站的件
 * 显示的是「取件出示快递单号后五位 76416」，而这条信息永远不会变成一条短信）。
 * 这种件在亮码里看不见，所以**漏的不是解析，是收录**。
 *
 * 判据只用一条可靠事实：**用户手动录过件**。
 * 手动录入 = 这个件的信息没被任何一条推送通道送到 = 有盲区。它是用户自己的行为留下的证据，
 * 不需要猜平台、不需要联网、不需要读别人的 App。
 *
 * ⚠️ 反过来不成立：**没有手动记录不等于没有盲区**。用户可能只是还没遇上那条不推的件，
 * 或者遇上了但没想起来录——那正是漏掉的那一类。所以这一格只能用来**在证据出现后开始提醒**，
 * 绝不能用来宣称「没有盲区的人不会被漏」。这条边界是整个功能诚实性的底线。
 */
object BlindSpot {

    /**
     * 有盲区 = 历史上出现过手动录入的件。
     *
     * 只看 `Source.MANUAL`，不看通知/短信的条数：一个用户可能同时有顺畅的短信通道
     * 和一条完全不推的平台（拼多多正是这样），后者才是这里要抓的东西。
     */
    fun detected(sources: Set<Source>): Boolean = Source.MANUAL in sources

    /**
     * 该不该提醒。三档全过才响：
     * ① 有盲区证据；② 用户开了这个提醒；③ 距上次提醒够了一整天。
     *
     * 「够了一整天」用天数差而不是毫秒差：提醒的意义是「今天出门前对一眼」，
     * 按 24 小时滚动的会在半夜重复响，而按自然日不会。
     */
    fun shouldRemind(
        sources: Set<Source>,
        remindersOn: Boolean,
        lastRemindedDay: Int?,
        today: Int,
    ): Boolean = remindersOn &&
        detected(sources) &&
        lastRemindedDay != today

    /** 提醒的文案分两种：第一次要说清为什么开始提醒他，之后只是催一下。 */
    fun isFirstReminder(lastRemindedAt: Long?): Boolean = lastRemindedAt == null
}
