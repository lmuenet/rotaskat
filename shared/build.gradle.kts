plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Bewusst eine reine Kotlin-JVM-Library statt Kotlin Multiplatform: Android
// und der Ktor-Server laufen beide auf der JVM, damit reicht ein simples
// Modul. Das haelt den Build klein und die Toolchain unkompliziert.
kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
