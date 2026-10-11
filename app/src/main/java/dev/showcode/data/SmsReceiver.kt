package dev.showcode.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dev.showcode.app
import dev.showcode.core.Source
import dev.showcode.core.parse.IncomingMessage

/**
 * 短信通道。长短信会被运营商拆成多段投递，必须拼回完整正文再解析，
 * 否则取件码正好落在第二段时就会静默失败。
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isNullOrEmpty()) return
        val first = parts.first()
        val message = IncomingMessage(
            sender = first.originatingAddress ?: "",
            body = parts.joinToString("") { it.displayMessageBody ?: "" },
            receivedAt = first.timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis(),
            channel = Source.SMS,
        )
        // 广播进程随时可能被杀，用 goAsync() 撑到落库完成，否则「什么都没做码就出现」会偶发失效。
        val pending = goAsync()
        context.app.repository.ingest(message).invokeOnCompletion { pending.finish() }
    }
}
