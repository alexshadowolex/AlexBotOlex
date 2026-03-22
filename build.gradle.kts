
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    val kotlinVersion = "2.3.0"

    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion

    id("org.jetbrains.kotlin.plugin.compose") version kotlinVersion

    id("org.jetbrains.compose") version "1.7.0"
}

group = "me.shika"
version = "1.0.0"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    val ktorVersion = "3.4.0"

    implementation(compose.desktop.currentOs)

    implementation("org.slf4j:slf4j-simple:2.0.5")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.2")

    implementation("org.apache.commons:commons-text:1.15.0")

    implementation("com.github.kwhat:jnativehook:2.2.2")
    implementation("com.github.twitch4j:twitch4j:1.25.0")
    implementation("com.adamratzman:spotify-api-kotlin-core:4.0.2")

    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder:$ktorVersion")
    implementation("io.ktor:ktor-server-partial-content:$ktorVersion")
    implementation("io.ktor:ktor-server-auto-head-response:$ktorVersion")

    implementation("com.google.api-client:google-api-client:2.8.1")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("com.google.apis:google-api-services-sheets:v4-rev20230227-2.0.0")

    implementation("dev.kord:kord-core:0.18.1")

    implementation(kotlin("script-runtime"))
}

tasks.withType<KotlinCompile> {
    kotlin.sourceSets.all {
        languageSettings.apply {
            optIn("kotlin.RequiresOptIn")
            optIn("kotlin.time.ExperimentalTime")
        }
    }

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)

        freeCompilerArgs.addAll(
            "-Xcontext-receivers",
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlin.time.ExperimentalTime"
        )
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "AlexBotOlex"
            packageVersion = version.toString()
        }
    }
}