package com.ejemplo.smsforwarder

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.net.HttpURLConnection
import java.net.URL

class NotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras ?: return
        val sender = extras.getCharSequence("android.title")?.toString() ?: "unknown"
        val body = extras.getCharSequence("android.text")?.toString() ?: "unknown"

        val prefs = getSharedPreferences("sms_config", Context.MODE_PRIVATE)
        val apiUrl = prefs.getString("api_url", "")?.trim() ?: ""
        if (apiUrl.isEmpty()) return

        val token = prefs.getString("api_token", "")?.trim() ?: ""

        Thread {
            enviarPost(apiUrl + "/v1/message", token, sbn.packageName, sender, body)
        }.start()
    }

    private fun escapeJson(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    private fun enviarPost(
        urlStr: String,
        token: String,
        pkg: String,
        sender: String,
        body: String
    ) {
        try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                if (token.isNotEmpty()) {
                    setRequestProperty("X-Api-Key", "$token")
                }
                doOutput = true
            }

            val payload = """
                {"platform":"${escapeJson(pkg)}","sender":"${escapeJson(sender)}","content":"${escapeJson(body)}"}
            """.trimIndent()

            conn.outputStream.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
            }
            conn.responseCode
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}