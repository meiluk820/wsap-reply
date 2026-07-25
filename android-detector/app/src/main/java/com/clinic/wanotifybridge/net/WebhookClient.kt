package com.clinic.wanotifybridge.net

import android.content.Context
import android.util.Log
import com.clinic.wanotifybridge.data.BridgeSettings
import com.clinic.wanotifybridge.data.MessageEvent
import com.clinic.wanotifybridge.util.FailureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Posts a [MessageEvent] to the configured webhook, retrying transient failures.
 *
 * Three attempts total, backing off 2s then 6s. A 4xx other than 408/429 is treated as
 * permanent (bad secret, wrong URL) — retrying those just burns battery, so they go
 * straight to the failure log.
 */
object WebhookClient {

    private const val TAG = "WebhookClient"
    private const val MAX_ATTEMPTS = 3
    private val BACKOFF_MILLIS = longArrayOf(2_000L, 6_000L)
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun send(context: Context, event: MessageEvent): Boolean = withContext(Dispatchers.IO) {
        val settings = BridgeSettings.get(context)
        val url = settings.webhookUrl
        val secret = settings.webhookSecret
        if (url.isEmpty() || secret.isEmpty()) {
            FailureLog.record(context, event.sender, "not configured: missing webhook URL or secret")
            return@withContext false
        }

        val payload = event.toJson()
        var lastError = "unknown"

        for (attempt in 0 until MAX_ATTEMPTS) {
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("X-Webhook-Secret", secret)
                .header("User-Agent", "WaNotifyBridge/1.0")
                .post(payload.toRequestBody(JSON))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return@withContext true
                    lastError = "HTTP ${response.code}"
                    if (!isRetryable(response.code)) {
                        FailureLog.record(context, event.sender, "$lastError (not retried)")
                        return@withContext false
                    }
                }
            } catch (e: IOException) {
                lastError = e.javaClass.simpleName + ": " + (e.message ?: "io error")
                Log.w(TAG, "attempt ${attempt + 1} failed", e)
            }

            if (attempt < MAX_ATTEMPTS - 1) delay(BACKOFF_MILLIS[attempt])
        }

        FailureLog.record(context, event.sender, "$lastError after $MAX_ATTEMPTS attempts")
        false
    }

    private fun isRetryable(code: Int): Boolean =
        code >= 500 || code == 408 || code == 429
}
