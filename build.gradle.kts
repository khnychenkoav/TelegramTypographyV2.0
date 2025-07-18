plugins {
    kotlin("jvm") version "2.0.0"
    application
}

group = "com.example"
version = "1.0-SNAPSHOT"


dependencies {
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.3.0")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("MainKt")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs += "-Xjvm-default=all"
        jvmTarget = "21"
    }
}