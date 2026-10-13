// The code both apps run: Kotlin Multiplatform, the open-frame pattern in Kotlin
// (docs/architecture.md). commonMain is everything platform-neutral; androidMain
// holds the Android side of each seam — existing Android code, moved verbatim —
// and iosMain its iOS twin. The app module and iosApp/ are thin hosts.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    androidLibrary {
        namespace = "io.raylytics.justmyweather.shared"
        compileSdk = 36
        minSdk = 24

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
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }
        // The Android side of the seams, at the versions the app shipped with:
        // OkHttp under HttpTransport, DataStore under every settings store.
        androidMain.dependencies {
            implementation("com.squareup.okhttp3:okhttp:4.12.0")
            implementation("androidx.datastore:datastore-preferences:1.1.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("androidHostTest").dependencies {
            implementation("org.junit.jupiter:junit-jupiter-api:5.11.3")
            implementation("org.junit.jupiter:junit-jupiter-params:5.11.3")
            runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.3")
            implementation("org.mockito:mockito-core:5.14.2")
            implementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
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
