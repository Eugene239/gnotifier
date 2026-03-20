package io.eugene239.gnotifier

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

enum class SendResult {
    Success,
    BadRequest,
    TelegramError,
}

object TelegramNotifier {
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
        val response = client.post(url) {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(body)
        }
        if (response.status != HttpStatusCode.OK) {
            return SendResult.TelegramError
        }
        val responseText = response.bodyAsText()
        if (!responseText.contains("\"ok\":true")) {
            if (responseText.contains("\"error_code\":400") || responseText.contains("\"error_code\": 400")) {
                return SendResult.BadRequest
            }
            return SendResult.TelegramError
        }
        return SendResult.Success
    }

    private fun buildFormBody(chatId: String, text: String): String {
        val enc = StandardCharsets.UTF_8.name()
        return "chat_id=${URLEncoder.encode(chatId, enc)}&text=${URLEncoder.encode(text, enc)}"
    }
}
