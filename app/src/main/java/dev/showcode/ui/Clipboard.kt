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
