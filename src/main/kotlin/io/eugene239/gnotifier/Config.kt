package io.eugene239.gnotifier

data class Config(
    val telegramBotToken: String,
    val telegramChatId: String,
    val notifyBearerToken: String,
    val port: Int,
) {
    companion object {
        fun fromEnv(): Config {
            val token = env("TELEGRAM_BOT_TOKEN")
            val chatId = env("TELEGRAM_CHAT_ID")
            val bearer = env("NOTIFY_BEARER_TOKEN")
            val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
            require(port in 1..65535) { "PORT must be 1-65535" }
            return Config(
                telegramBotToken = token,
                telegramChatId = chatId,
                notifyBearerToken = bearer,
                port = port,
            )
        }

        private fun env(name: String): String =
            System.getenv(name)?.takeIf { it.isNotBlank() }
                ?: error("Missing or empty required environment variable: $name")
    }
}
