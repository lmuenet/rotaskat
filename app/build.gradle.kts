plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.rotaskat.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.rotaskat.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Die Server-Adresse ist keine Konstante im Code, sondern kommt aus
        // der Umgebung. Lokal per local.properties oder Env-Variable, im CI
        // aus den GitHub Secrets.
        buildConfigField(
            "String",
            "DEFAULT_SERVER_URL",
            "\"${providers.gradleProperty("rotaskat.serverUrl").orNull ?: System.getenv("ROTASKAT_SERVER_URL") ?: ""}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests {
            // Robolectric braucht die gemergten Ressourcen und das Manifest,
            // sonst startet kein Anwendungskontext.
            isIncludeAndroidResources = true
        }
    }
}

// Room schreibt das Schema jeder Version als JSON mit. Ohne diese Dateien gibt
// es spaeter keinen Migrationstest, sondern nur die Hoffnung, dass die
// handgeschriebene Migration zum erwarteten Schema fuehrt.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    // Sync laeuft als WorkManager-Job: in der Kneipe gibt es haeufig kein Netz,
    // der Upload muss den Prozesstod ueberleben und spaeter nachziehen.
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit4)
    // Kein Emulator, keine androidTest-Instrumentierung: die Datenschicht wird
    // mit Robolectric im normalen Unit-Test-Lauf geprueft. Der schnelle
    // CI-Job kommt damit ohne Android-Geraet aus.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
