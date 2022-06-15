
import org.jetbrains.compose.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"
    id("org.jetbrains.compose") version "1.1.1"
}

group = "me.shika"
version = "1.2.0"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    val ktorVersion = "1.6.8"

    implementation(compose.desktop.currentOs)

    implementation("org.slf4j:slf4j-simple:1.7.36")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")

    implementation("com.github.kwhat:jnativehook:2.2.2")
    implementation("com.github.twitch4j:twitch4j:1.10.0")
    implementation("com.adamratzman:spotify-api-kotlin-core:3.8.6")
    implementation("javazoom:jlayer:1.0.1")
    implementation("org:jaudiotagger:2.0.3")

    /*implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")*/
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization:$ktorVersion")


    implementation(kotlin("script-runtime"))
}

tasks.withType<KotlinCompile> {
    kotlin.sourceSets.all {
        languageSettings.apply {
            optIn("kotlin.RequiresOptIn")
            optIn("kotlin.time.ExperimentalTime")
        }
    }

    kotlinOptions {
        jvmTarget = "17"
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