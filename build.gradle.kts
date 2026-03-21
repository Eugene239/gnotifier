import java.io.File
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    id("io.ktor.plugin") version "3.4.1"
}

group = "io.eugene239.gnotifier"
version = "1.0.0"

fun gitShortRev(dir: File): String =
    try {
        val p = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .directory(dir)
            .redirectErrorStream(true)
            .start()
        p.inputStream.bufferedReader().readText().trim().ifEmpty { "unknown" }
    } catch (_: Exception) {
        "unknown"
    }

tasks.register("generateBuildInfo") {
    val out = layout.buildDirectory.dir("generated/resources/main")
    outputs.dir(out)
    doLast {
        val dir = out.get().asFile
        dir.mkdirs()
        var sha = (project.findProperty("buildInfoSha") as String?)
            ?: System.getenv("GIT_SHA")
            ?: gitShortRev(rootDir)
        if (sha.length > 7) {
            sha = sha.take(7)
        }
        val dateStr = (project.findProperty("buildInfoDate") as String?)
            ?: System.getenv("BUILD_DATE")
            ?: ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMddHH"))
        val version = "$dateStr-$sha"
        File(dir, "build-info.properties").writeText("version=$version\n")
    }
}

tasks.named("processResources") {
    dependsOn("generateBuildInfo")
}

application {
    mainClass.set("io.eugene239.gnotifier.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-java")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.16")
}

kotlin {
    jvmToolchain(21)
}

sourceSets {
    main {
        resources {
            srcDir(layout.buildDirectory.dir("generated/resources/main"))
        }
    }
}
