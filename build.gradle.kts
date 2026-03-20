plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    id("io.ktor.plugin") version "3.4.1"
}

group = "io.eugene239.gnotifier"
version = "1.0.0"

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
