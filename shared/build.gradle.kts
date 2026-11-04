// The code both apps run: Kotlin Multiplatform, the open-frame pattern in Kotlin
// (docs/architecture.md). commonMain is everything platform-neutral; androidMain
// holds the Android side of each seam — existing Android code, moved verbatim —
// and iosMain its iOS twin. The app module and iosApp/ are thin hosts.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    androidLibrary {
        namespace = "io.raylytics.justmyweather.shared"
        compileSdk = 36
        minSdk = 24
        // Compose resources (the shared string catalog) ride in the Android
        // library's assets; the KMP library plugin leaves those off by default.
        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true

        // The pure-logic suites moved here from app/src/test unchanged, so
        // they keep running on the JVM under JUnit 5 exactly as before.
        withHostTestBuilder {}.configure {
            isIncludeAndroidResources = false
            isReturnDefaultValues = true
        }

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // The framework iosApp/ links. Simulator slice for local development and
    // the iOS gate; device slice for TestFlight. No x64: iPhone-only, Apple
    // silicon Macs.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
            languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
            // BackHandler: common in Compose Multiplatform, still marked experimental.
            languageSettings.optIn("androidx.compose.ui.ExperimentalComposeUiApi")
        }
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
            // Multiplatform since 1.1: the settings stores compile here unchanged,
            // so Android keeps the very same keys and file it shipped with.
            api("androidx.datastore:datastore-preferences-core:1.3.0")
            // Dates and zones in every model. On Android it is java.time
            // underneath, so arithmetic and zone rules are the ones the app
            // always used; formatting stays on java.time itself (TimeFormat.kt).
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")

            // The UI. Compose Multiplatform 1.11.1 is androidx Compose 1.11.2 /
            // Material 3 1.4.0 on Android — the versions the app's BOM pins
            // (just-my-weather-nyt.1), so Android draws with exactly what it did.
            api("org.jetbrains.compose.runtime:runtime:1.12.2")
            api("org.jetbrains.compose.foundation:foundation:1.12.2")
            api("org.jetbrains.compose.ui:ui:1.12.2")
            api("org.jetbrains.compose.material3:material3:1.9.0")
            implementation("org.jetbrains.compose.ui:ui-backhandler:1.12.2")
            implementation("org.jetbrains.compose.components:components-resources:1.12.2")
            api("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
            api("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
        }
        // The Android side of the seams, at the versions the app shipped with:
        // OkHttp under HttpTransport, DataStore under every settings store.
        androidMain.dependencies {
            implementation("com.squareup.okhttp3:okhttp:5.5.1")
            implementation("androidx.datastore:datastore-preferences:1.3.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("androidHostTest").dependencies {
            implementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
            implementation("org.junit.jupiter:junit-jupiter-params:6.1.3")
            runtimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.3")
            implementation("org.mockito:mockito-core:5.24.0")
            implementation("org.mockito.kotlin:mockito-kotlin:6.4.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
    }
}

// The shared string catalog: shared/src/commonMain/composeResources/values/strings.xml,
// read as Res.string.* (decision 9: one translation per language, both apps).
compose.resources {
    packageOfResClass = "io.raylytics.justmyweather.resources"
    publicResClass = false
    generateResClass = always
}
