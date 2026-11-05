import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
}

// Optional release signing. Absent on contributor machines and CI, so the
// release build silently falls back to unsigned — debug builds never need it.
// Populate app/keystore.properties (gitignored) to sign locally.
val keystorePropertiesFile = rootProject.file("app/keystore.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            load(FileInputStream(keystorePropertiesFile))
        }
    }

// Ad and consent ids (google-ads-exit Stage B): the Appodeal app key and the
// InMobi CMP p-code, from app/ads.properties (gitignored; copy
// ads.properties.example). Both ship in the binary, so they are not secrets,
// but they stay out of git so a clone without them builds an app that shows
// no ads (blank ids mean no consent request, no SDK and no banner — see
// ads/AdsRules), and so scripts/android/release-internal.sh can refuse a cut
// that still carries a blank or a placeholder. Debug builds serve Appodeal's
// test ads whatever the key (Appodeal.setTesting).
val adsPropertiesFile = rootProject.file("app/ads.properties")
val adsProperties =
    Properties().apply {
        if (adsPropertiesFile.exists()) {
            load(FileInputStream(adsPropertiesFile))
        }
    }

/** A Kotlin string literal for BuildConfig: the value, escaped, or "". */
fun adsId(name: String): String {
    val v = adsProperties.getProperty(name).orEmpty().trim()
    return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

android {
    namespace = "io.raylytics.justmyweather"
    // Google Play has required new apps and updates to target API 36 since
    // 2026-08-31; the internal track is not exempt. AGP 8.13 is the matching
    // toolchain (8.7 only warned at 36).
    compileSdk = 37

    defaultConfig {
        applicationId = "io.raylytics.justmyweather"
        minSdk = 24
        targetSdk = 36
        // versionCode is bumped per upload by scripts/android/bump-version-code.sh;
        // versionName is the release (CLAUDE.md "Versioning"). 0.2.0 is the first
        // Play build: everything since the v0.1.x sideloads.
        versionCode = 12
        versionName = "0.4.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Ship only the English resources we wrote. Without this, the AAB
        // carries every locale AndroidX / Compose / Material 3 translate
        // into. Add target locales here when in-app copy is localised.
        // en-rUS carries only the widget picker's Fahrenheit sample
        // (values-en-rUS); without it listed, "en" alone strips it.
        resourceConfigurations += listOf("en", "en-rUS")

        // Read by JustMyWeatherApp, which picks the InMobi gateway and starts
        // Appodeal only when both are present (ads/AdsRules.configured).
        buildConfigField("String", "APPODEAL_APP_KEY", adsId("appodealAppKey"))
        buildConfigField("String", "INMOBI_PCODE", adsId("inmobiPcode"))
    }

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties.getProperty("storeFile") ?: "release.keystore")
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// Kotlin 2.3 (bumped 2026-10-03 for the Play Billing SDK and the since-removed
// Google Mobile Ads SDK, whose metadata 2.1 could not read) retired the
// kotlinOptions DSL.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // kotlin.time.Instant, which the shared models carry.
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Weather data layer: NWS over HTTP, JSON wire shapes parsed with
    // kotlinx.serialization. No proprietary SDKs — keeps the app
    // open-source-friendly (e.g. F-Droid) and the data path legible.
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")

    // Persistence for the user's view config, theme, location, and alert
    // rules. DataStore over SharedPreferences for the flow-based reads.
    implementation("androidx.datastore:datastore-preferences:1.3.0")

    // Background polling for personal alerts, and the widgets' refresh.
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    // Home screen widgets. Glance is Compose-flavoured RemoteViews: the same
    // Row/Column/Text vocabulary, drawn by the launcher. It pulls a Compose
    // runtime a patch ahead of the BOM's (1.7.8 over 1.7.6), which the two
    // libraries tolerate within a minor version.
    implementation("androidx.glance:glance-appwidget:1.2.0")

    // The purchase that removes the one banner ad (Evan, 2026-10-03). It stays
    // behind billing/ so a build without it (F-Droid, say) would drop one
    // package and one call site. Play requires Billing Library 8+ for new apps
    // since 2026-08-31. The ad SDK itself lives behind ads/: Google Mobile Ads
    // and its consent platform (UMP) were removed in the google-ads-exit epic
    // (Stage A); Appodeal behind the InMobi CMP replaced them (Stage B, below).
    implementation("com.android.billingclient:billing-ktx:9.1.0")

    // google-ads-exit Stage B (raylytics/docs/ad-migration/RUNBOOK.md):
    // Appodeal mediation on Appodeal's own network accounts, behind the
    // InMobi CMP. Appodeal's Maven repository is in settings.gradle.kts. Every
    // version is an exact pin at least two weeks old (runbook hard rule): core
    // 4.4.0 (2026-09-22) and, for each network, the adapter current when
    // 4.4.0 shipped — the same rule the iOS packages in project.yml follow, so
    // bump them together. Left out on purpose: AdMob and Google Ad Manager
    // (the suspended account), Meta and Yandex (own-account networks we do not
    // hold), Notsy, the other mediators' stacks, and the service adapters
    // (Adjust, AppsFlyer, Firebase, Facebook analytics); the guardrail
    // (scripts/check-no-google-ads.sh) refuses the first group. Core brings
    // Appodeal's own consent manager and, through it, Google's UMP along
    // transitively; nothing here names either and nothing in the app calls
    // them (the guardrail enforces both) — the InMobi CMP governs.
    implementation("com.appodeal.ads.sdk:core:4.4.0")
    implementation("com.appodeal.ads.sdk.adapters:iab:1.8.1.1")
    implementation("com.appodeal.ads.sdk.adapters:applovin:13.6.4.0")
    implementation("com.appodeal.ads.sdk.adapters:bidmachine:3.8.0.0")
    implementation("com.appodeal.ads.sdk.adapters:dt_exchange:8.4.7.0")
    implementation("com.appodeal.ads.sdk.adapters:inmobi:11.4.1.0")
    implementation("com.appodeal.ads.sdk.adapters:mintegral:17.1.81.0")
    implementation("com.appodeal.ads.sdk.adapters:my_target:5.51.2.0")
    implementation("com.appodeal.ads.sdk.adapters:unity_ads:4.20.1.0")
    implementation("com.appodeal.ads.sdk.adapters:vungle:7.7.8.0")

    // The InMobi CMP (Choice; Maven Central), driven by ads/InMobiConsentGateway.
    // Its POM declares no dependencies, so the libraries its classes use
    // (read from the 2.4.3 AAR: AppCompat and Material for its bottom-sheet
    // UI, RecyclerView, CardView, ConstraintLayout, LiveData, Gson, and the
    // IAB GPP encoder) are declared here; a Compose-only app carries none of them.
    implementation("com.inmobi:inmobicmp:2.4.3")
    implementation("com.iabgpp:iabgpp-encoder:3.2.3")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("com.google.code.gson:gson:2.13.2")
    // Google's ads SDK pulled in a pre-1.3 androidx.fragment, and release lint
    // (lintVitalRelease) refuses an app that registers for activity results
    // on one (InvalidFragmentVersionForActivityResult). The pin stays so the
    // next ad SDK cannot drag the floor back down.
    implementation("androidx.fragment:fragment:1.9.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
    testImplementation("org.junit.jupiter:junit-jupiter-params:6.1.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
    testImplementation("org.mockito:mockito-core:5.24.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.4.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // Pinned ahead of what compose-bom drags in. Espresso 3.5 resolves
    // android.hardware.input.InputManager.getInstance by reflection, and that
    // method is gone in Android 17 — every instrumented test dies at
    // Espresso.onIdle before its body runs. See just-my-weather-1te.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
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
