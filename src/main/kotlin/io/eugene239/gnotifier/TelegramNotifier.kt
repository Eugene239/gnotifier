package io.eugene239.gnotifier

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class SendResult {
    Success,
    BadRequest,
    TelegramError,
}

object TelegramNotifier {
    private val log = LoggerFactory.getLogger(TelegramNotifier::class.java)
    private const val TELEGRAM_MAX_MESSAGE = 4096

    suspend fun send(client: HttpClient, config: Config, text: String): SendResult {
        val payload = if (text.length > TELEGRAM_MAX_MESSAGE) {
            text.take(TELEGRAM_MAX_MESSAGE)
        } else {
            text
        }
        val url =
            "https://api.telegram.org/bot${config.telegramBotToken}/sendMessage"
        val body = buildFormBody(config.telegramChatId, payload)
        log.info(
            "Telegram request: POST https://api.telegram.org/bot***/sendMessage textLength={} text={}",
            payload.length,
            truncateForLog(payload),
        )
        return try {
            val response = client.post(url) {
                header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
                setBody(body)
            }
            if (response.status != HttpStatusCode.OK) {
                log.warn("Telegram HTTP {}: {}", response.status, response.bodyAsText())
                return SendResult.TelegramError
            }
            val responseText = response.bodyAsText()
            if (!responseText.contains("\"ok\":true")) {
                log.warn("Telegram API not ok: {}", responseText)
                if (responseText.contains("\"error_code\":400") || responseText.contains("\"error_code\": 400")) {
                    return SendResult.BadRequest
                }
                return SendResult.TelegramError
            }
            SendResult.Success
        } catch (e: Exception) {
            log.error("Telegram send failed", e)
            SendResult.TelegramError
        }
    }

    private fun buildFormBody(chatId: String, text: String): String {
        val enc = StandardCharsets.UTF_8.name()
        return "chat_id=${URLEncoder.encode(chatId, enc)}&text=${URLEncoder.encode(text, enc)}"
    }
}
