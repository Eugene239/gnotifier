package io.eugene239.gnotifier

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.contentType
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

internal const val MAX_BODY_BYTES = 8192

private val log = LoggerFactory.getLogger("io.eugene239.gnotifier")

fun main() {
    val config = Config.fromEnv()
    val httpClient = HttpClient(Java)
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = false
                    explicitNulls = false
                },
            )
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.application.log.error("Unhandled failure", cause)
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
        monitor.subscribe(ApplicationStarted) {
            launch {
                when (TelegramNotifier.send(httpClient, config, "Gnotifier Started")) {
                    SendResult.Success -> { }
                    else -> log.warn("Startup Telegram notification failed")
                }
            }
        }
        routing {
            post("/notify") {
                if (!call.validateBearer(config)) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                val ct = call.request.contentType()
                val len = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (len != null && len > MAX_BODY_BYTES) {
                    call.respond(HttpStatusCode.PayloadTooLarge)
                    return@post
                }
                val text: String = when {
                    ct.match(ContentType.Application.Json) -> {
                        val payload = try {
                            call.receive<NotifyRequest>()
                        } catch (e: BadRequestException) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@post
                        } catch (e: SerializationException) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@post
                        } catch (e: JsonConvertException) {
                            call.respond(HttpStatusCode.BadRequest)
                            return@post
                        }
                        payload.message
                    }
                    ct.match(ContentType.Text.Plain) -> {
                        val body = call.receiveText()
                        if (body.length > MAX_BODY_BYTES) {
                            call.respond(HttpStatusCode.PayloadTooLarge)
                            return@post
                        }
                        body
                    }
                    ct.match(ContentType.Application.FormUrlEncoded) -> {
                        val message = call.receiveParameters()["message"] ?: run {
                            call.respond(HttpStatusCode.BadRequest)
                            return@post
                        }
                        message
                    }
                    else -> {
                        call.respond(HttpStatusCode.UnsupportedMediaType)
                        return@post
                    }
                }
                if (text.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                if (text.length > MAX_BODY_BYTES) {
                    call.respond(HttpStatusCode.PayloadTooLarge)
                    return@post
                }
                val clientIp = call.request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
                    ?: call.request.local.remoteHost
                log.info(
                    "Incoming notify: method={} uri={} remote={} contentType={} contentLength={} message={}",
                    call.request.httpMethod.value,
                    call.request.local.uri,
                    clientIp,
                    ct,
                    len?.toString() ?: "chunked/unknown",
                    truncateForLog(text),
                )
                when (TelegramNotifier.send(httpClient, config, text)) {
                    SendResult.Success -> call.respond(HttpStatusCode.NoContent)
                    SendResult.BadRequest -> call.respond(HttpStatusCode.BadRequest)
                    SendResult.TelegramError -> call.respond(HttpStatusCode.BadGateway)
                }
            }
        }
    }.start(wait = true)
}

private fun ApplicationCall.validateBearer(config: Config): Boolean {
    val header = request.headers[HttpHeaders.Authorization] ?: return false
    val prefix = "Bearer "
    if (!header.startsWith(prefix)) return false
    val token = header.removePrefix(prefix).trim()
    return token == config.notifyBearerToken
}

internal const val LOG_TEXT_MAX = 2048

internal fun truncateForLog(text: String, max: Int = LOG_TEXT_MAX): String =
    if (text.length <= max) {
        text
    } else {
        text.take(max) + "... [truncated, total ${text.length} chars]"
    }
