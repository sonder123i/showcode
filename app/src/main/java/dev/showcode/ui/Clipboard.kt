package dev.showcode.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

/**
 * 长按取件码复制（对应在位者 issue #52 的诉求）。
 * Android 13+ 系统自己会弹复制确认，但这里仍然给一个 Toast：
 * 屏幕上写着「长按可复制」，用户长按之后必须得到本 App 的回应，否则他会怀疑没生效。
 */
fun copyCode(context: Context, code: String, @StringRes confirmationRes: Int) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
    Toast.makeText(context, confirmationRes, Toast.LENGTH_SHORT).show()
}

/**
 * 读剪贴板的第一条纯文本；读不到就是 null，**不猜**。
 *
 * 三种「读不到」在设备上没法区分，也不值得区分：剪贴板本来就是空的、内容不是文本
 * （图片 / 文件 / URI 那一类）、以及系统不让读（Android 10+ 要求 App 处于焦点，
 * 部分 ROM 更干脆抛 SecurityException）。所以异常必须吃掉——调用它的那一次是
 * 「进屏自动填」，它失败的正确答案是安静地什么都不做，而不是崩一次给用户看。
 * 用户真想搬那段文本时还有显式粘贴那一下。
 */
fun readText(context: Context): String? = runCatching {
    val clip = context.getSystemService(ClipboardManager::class.java).primaryClip ?: return null
    if (clip.itemCount == 0) return null
    clip.getItemAt(0).text?.toString()?.takeIf { it.isNotBlank() }
}.getOrNull()
