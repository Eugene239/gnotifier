package io.eugene239.gnotifier

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal const val MAX_BODY_BYTES = 8192

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
        routing {
            post("/notify") {
                if (!call.validateBearer(config)) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                val ct = call.request.contentType()
                if (!ct.match(ContentType.Application.Json)) {
                    call.respond(HttpStatusCode.UnsupportedMediaType)
                    return@post
                }
                val len = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (len != null && len > MAX_BODY_BYTES) {
                    call.respond(HttpStatusCode.PayloadTooLarge)
                    return@post
                }
                val payload = try {
                    call.receive<NotifyRequest>()
                } catch (e: SerializationException) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                } catch (e: JsonConvertException) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                val text = payload.message
                if (text.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                if (text.length > MAX_BODY_BYTES) {
                    call.respond(HttpStatusCode.PayloadTooLarge)
                    return@post
                }
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
