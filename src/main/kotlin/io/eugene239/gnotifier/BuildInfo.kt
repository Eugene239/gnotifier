package io.eugene239.gnotifier

import java.util.Properties

object BuildInfo {
    val version: String = loadVersion()

    private fun loadVersion(): String {
        val stream = BuildInfo::class.java.classLoader.getResourceAsStream("build-info.properties")
            ?: return "dev-local"
        return stream.use {
            val p = Properties()
            p.load(it)
            p.getProperty("version", "unknown-unknown").trim().ifEmpty { "unknown-unknown" }
        }
    }
}
