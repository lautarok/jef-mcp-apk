package com.ejemplo.smsforwarder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class MainActivity : Activity() {
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val barColor = Color.parseColor("#121212")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = barColor
            window.navigationBarColor = barColor
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(
                WebAppInterface(
                    context = this@MainActivity,
                    onGetComplete = { ok, payload ->
                        runOnUiThread {
                            val escaped = escapeJsString(payload)
                            evaluateJavascript("onReglasGetResult($ok, '$escaped')", null)
                        }
                    },
                    onSyncComplete = { ok, msg ->
                        runOnUiThread {
                            val escaped = escapeJsString(msg)
                            evaluateJavascript("onReglasSyncResult($ok, '$escaped')", null)
                        }
                    }
                ),
                "AndroidBridge"
            )
            loadUrl("file:///android_asset/index.html")
        }

        setContentView(webView)
    }

    private fun escapeJsString(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\r", "")
            .replace("\n", " ")
}

class WebAppInterface(
    private val context: Context,
    private val onGetComplete: (Boolean, String) -> Unit,
    private val onSyncComplete: (Boolean, String) -> Unit
) {
    private val prefs = context.getSharedPreferences("sms_config", Context.MODE_PRIVATE)

    @JavascriptInterface
    fun guardarConfig(url: String, token: String) {
        prefs.edit()
            .putString("api_url", url)
            .putString("api_token", token)
            .apply()
    }

    @JavascriptInterface
    fun getUrl(): String = prefs.getString("api_url", "") ?: ""

    @JavascriptInterface
    fun getToken(): String = prefs.getString("api_token", "") ?: ""

    @JavascriptInterface
    fun guardarReglasJson(json: String) {
        prefs.edit()
            .putString("rules_json", json)
            .apply()
    }

    @JavascriptInterface
    fun getRulesJson(): String = prefs.getString("rules_json", "") ?: ""

    @JavascriptInterface
    fun obtenerReglasGet() {
        val baseUrl = (prefs.getString("api_url", "") ?: "").trim()
        val token = (prefs.getString("api_token", "") ?: "").trim()

        if (baseUrl.isEmpty()) {
            onGetComplete(false, "Falta configurar la URL del servidor.")
            return
        }

        Thread {
            var conn: HttpURLConnection? = null
            try {
                val targetUrl = buildConfigEndpointUrl(baseUrl)
                conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/json")
                    if (token.isNotEmpty()) {
                        setRequestProperty("X-Api-Key", token)
                    }
                }

                val code = conn.responseCode
                val bodyStream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseText = bodyStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""

                if (code in 200..299) {
                    onGetComplete(true, responseText)
                } else {
                    val detail = if (responseText.isNotBlank()) " - $responseText" else ""
                    onGetComplete(false, "Error GET HTTP $code$detail")
                }
            } catch (e: Exception) {
                onGetComplete(false, "Error de red al obtener config: ${e.message ?: "fallo desconocido"}")
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    @JavascriptInterface
    fun enviarReglasPut(jsonBody: String) {
        val baseUrl = (prefs.getString("api_url", "") ?: "").trim()
        val token = (prefs.getString("api_token", "") ?: "").trim()

        if (baseUrl.isEmpty()) {
            onSyncComplete(false, "Guardado local, pero falta configurar la URL.")
            return
        }

        Thread {
            var conn: HttpURLConnection? = null
            try {
                val targetUrl = buildConfigEndpointUrl(baseUrl)
                conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    if (token.isNotEmpty()) {
                        setRequestProperty("X-Api-Key", token)
                    }
                }

                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(jsonBody)
                    writer.flush()
                }

                val code = conn.responseCode
                val bodyStream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseText = bodyStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""

                if (code in 200..299) {
                    onSyncComplete(true, "PUT /v1/config OK ($code). Reglas guardadas.")
                } else {
                    val detail = if (responseText.isNotBlank()) " - $responseText" else ""
                    onSyncComplete(false, "Error HTTP $code$detail")
                }
            } catch (e: Exception) {
                onSyncComplete(false, "Error de red: ${e.message ?: "fallo desconocido"}")
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    private fun buildConfigEndpointUrl(rawUrl: String): String {
        val cleaned = rawUrl.trim().trimEnd('/')
        if (cleaned.endsWith("/v1/config")) {
            return cleaned
        }
        val withoutApiSms = if (cleaned.endsWith("/api/sms")) {
            cleaned.removeSuffix("/api/sms")
        } else {
            cleaned
        }
        return "$withoutApiSms/v1/config"
    }

    @JavascriptInterface
    fun abrirPermisosNotificaciones() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    @JavascriptInterface
    fun abrirAjustesBateria() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}