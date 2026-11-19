import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

// FxA OAuth credentials come ONLY from local.properties (never committed).
// If absent, the app builds and runs, but Sign-in is disabled with an
// on-screen explanation (see sync/FxaSyncManager.kt).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val fxaClientId: String = localProps.getProperty("fxa.client.id", "")
val fxaRedirectUrl: String = localProps.getProperty("fxa.redirect.url", "")

// Versions verified against Mozilla's Maven repo on 2026-11-17:
//   service-firefox-accounts / browser-storage-sync / service-sync-logins /
//   feature-syncedtabs / browser-state / concept-sync  -> 160.0 (stable)
//   org.mozilla.geckoview:geckoview                    -> 160.0.20261104181552
//   (161.0b4 existed at that date but is a beta; the MVP pins the stable 160.0.)
val acVersion = "160.0"
val geckoviewVersion = "160.0.20261104181552"

android {
    namespace = "com.zensync.mvp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zensync.mvp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-mvp"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "FXA_CLIENT_ID", "\"$fxaClientId\"")
        buildConfigField("String", "FXA_REDIRECT_URL", "\"$fxaRedirectUrl\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.7.6"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Room (local workspaces + tabs)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager (periodic sync)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Encrypted storage for FxA tokens / prefs
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // GeckoView (rendering engine — NO Android WebView anywhere in this app)
    implementation("org.mozilla.geckoview:geckoview:$geckoviewVersion")

    // Mozilla Android Components — FxA + Sync (all pinned to the same 160.0)
    implementation("org.mozilla.components:service-firefox-accounts:$acVersion")
    implementation("org.mozilla.components:browser-storage-sync:$acVersion")
    implementation("org.mozilla.components:service-sync-logins:$acVersion")
    implementation("org.mozilla.components:feature-syncedtabs:$acVersion")
    implementation("org.mozilla.components:browser-state:$acVersion")
    implementation("org.mozilla.components:concept-sync:$acVersion")
    // AppServices FxA client (transitive via service-firefox-accounts, listed
    // explicitly so FxaConfig / FxaServer are available to our code).
    implementation("org.mozilla.appservices:fxaclient:$acVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
