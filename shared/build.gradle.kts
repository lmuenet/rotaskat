plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Bewusst eine reine Kotlin-JVM-Library statt Kotlin Multiplatform: Android
// und der Ktor-Server laufen beide auf der JVM, damit reicht ein simples
// Modul. Das haelt den Build klein und die Toolchain unkompliziert.
kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.property)
    // checkAll() ist suspend, die Tests brauchen runBlocking. Kotest zoege
    // Coroutines ohnehin transitiv herein, hier steht die Abhaengigkeit
    // explizit, weil der Testcode sie direkt importiert.
    testImplementation(libs.kotlinx.coroutines.core)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Erzeugt die Golden-Datei der Spielwerttabelle neu. Bewusst KEIN Teil von
// `test`: die Datei ist die handgepruefte Referenz der Skatordnung. Wuerde sie
// automatisch mitwandern, wuerde eine versehentliche Regelaenderung ihren
// eigenen Beweis mitliefern. Aufruf nur von Hand, wenn sich eine Regel
// absichtlich aendert, danach den Diff lesen.
tasks.register<JavaExec>("generateGameValueTable") {
    group = "verification"
    description = "Schreibt src/test/resources/scoring/spielwerte.tsv neu. Nur von Hand aufrufen."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.rotaskat.shared.scoring.GameValueTableGeneratorKt")
}
