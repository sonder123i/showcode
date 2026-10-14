package dev.showcode.core

import dev.showcode.core.parse.MessageGate

/**
 * 剪贴板那段文本**该不该自动填进手动导入**。
 *
 * 竞品是「剪贴板有文本就填」，而这一格有两个真实的坑：
 *
 * 1. 用户可能正打着一半。手动导入页的红线早就写过——失败时字要留在原地；
 *    那么**成功**的时候同样不许把他的字清掉。
 * 2. 剪贴板里最常见的不是取件短信，是验证码、聊天、网址。自动填一条不像取件的文本，
 *    等于把「这条要不要导入」这个决定替用户做掉，而这一屏本来就是给他做决定用的。
 *
 * 所以自动填走保守判据，而**显式粘贴按钮走另一套**（见 [explicitPaste]）：
 * 用户按下那个按钮就是在表达意图，这时候还拿判据拦他，是把「不打扰」做成了「不响应」。
 * 两条路不对称是有意的，不是漏了一条。
 */
object ClipboardIntake {

    /**
     * 自动填的原文长度上限。卡在这里而不是卡得更松，理由不是排版：
     * 超过这个长度的一整段，通常是**一页聊天转发**而不是「一条消息」，里面混着好几件。
     * 自动填它，等于替用户决定这一次导不进的是哪一件——那一件只能他自己挑。
     *
     * 真人取件短信在 100 字上下，所以 500 不会挡掉任何一条单发消息。
     */
    const val MAX_AUTO_IMPORT_LENGTH = 500

    /**
     * 进屏时那一次自动预填。
     *
     * @param draft 手动导入页当前已经存在的原文。非空就不许动它。
     * @param knownBodies 库里已经有过的正文（指纹集）。用 [bodyFingerprint] 是为了和
     *   未识别表、消息账本**同一个身份判据**——同一句话换个空格不该被当成两件事。
     */
    fun autoPrefill(clipboard: String?, draft: String, knownBodies: Set<String>): ClipboardVerdict {
        val text = clipboard?.trim().orEmpty()
        if (text.isEmpty()) return ClipboardVerdict.Skip(ClipboardSkip.EMPTY)
        // 先问草稿再问内容：用户正在打字的场合，「这段该不该填」根本不该被问到。
        if (draft.isNotBlank()) return ClipboardVerdict.Skip(ClipboardSkip.HAS_DRAFT)
        if (bodyFingerprint(text) in knownBodies) return ClipboardVerdict.Skip(ClipboardSkip.KNOWN)
        if (text.length > MAX_AUTO_IMPORT_LENGTH) return ClipboardVerdict.Skip(ClipboardSkip.TOO_LONG)
        if (!MessageGate.looksLikePickup(text)) return ClipboardVerdict.Skip(ClipboardSkip.NOT_PICKUP)
        return ClipboardVerdict.Fill(text)
    }

    /**
     * 用户显式点「粘贴」。只要剪贴板里有东西就填——包括草稿非空、包括不像取件、
     * 包括一整页聊天。上限也放开：他要的就是把那一整段搬过来。
     *
     * ⚠️ 这一条同时也是焦点限制失败时的退路：Android 10+ 要求 App 在前台有焦点才读得到
     * 剪贴板，自动那次读不到是系统行为，用户主动点这一下则有焦点、几乎总能成。
     * 所以真机验不过自动那条时，砍的是 [autoPrefill] 的调用点，不是这个函数。
     */
    fun explicitPaste(clipboard: String?): ClipboardVerdict {
        val text = clipboard?.trim().orEmpty()
        if (text.isEmpty()) return ClipboardVerdict.Skip(ClipboardSkip.EMPTY)
        return ClipboardVerdict.Fill(text)
    }
}

/** 填不填。[Fill] 带的是**修剪过首尾空白、内容原样**的文本——正文要回显给人核对，不许改写。 */
sealed interface ClipboardVerdict {
    data class Fill(val text: String) : ClipboardVerdict
    data class Skip(val reason: ClipboardSkip) : ClipboardVerdict
}

/**
 * 没填的原因。UI 层可以挑一句最轻的说，也可以整句不说——但判据必须留得下来，
 * 否则这一格将来出问题时没人知道当初是「判成不像取件」还是「读到的就是空」。
 */
enum class ClipboardSkip {
    /** 剪贴板是空的，或者读不到（没焦点、没权限、系统刚清掉）。 */
    EMPTY,

    /** 导入页已经有内容了，不许覆盖。 */
    HAS_DRAFT,

    /** 这段正文库里见过——多半已经是一张卡或一条未识别，再填一次是重复劳动。 */
    KNOWN,

    /** 太长了，更像一页聊天而不是一条消息。 */
    TOO_LONG,

    /** 读起来不像取件（验证码、营销、网址那一类）。 */
    NOT_PICKUP,
}
