plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    kotlin("plugin.serialization") version "1.9.22"
}

group = "top.stellortus.stellar_music_server"
version = "0.3"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

tasks.processResources {
    exclude("db/**", "tracks/**")
}

kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.partialContent)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)

    implementation(libs.logback.classic)

    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")

    implementation("org.jetbrains.exposed:exposed-core:0.50.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.50.1")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.85")
    implementation("org.slf4j:slf4j-simple:2.0.12")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("net.jthink:jaudiotagger:3.0.1")
    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}
