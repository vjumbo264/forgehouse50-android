plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    // FORGEBUILD: the generating AI replaces the namespace/applicationId per app.
    namespace = "com.forgebuild.forgehouse50"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.forgebuild.forgehouse50"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            // Lean by default: code shrinking + resource shrinking always on.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug { isMinifyEnabled = false }
    }

    signingConfigs {
        // Release signing for CI: the generating AI creates a keystore via
        // tools/make_keystore.sh and stores it base64-encoded in the app repo's
        // GitHub Actions secrets (KEYSTORE_BASE64, KEYSTORE_PASSWORD,
        // KEY_ALIAS, KEY_PASSWORD). See .github/workflows/release.yml.
        create("release") {
            val ksFile = rootProject.file("keystore/release.jks")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: "forgebuild"
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }
    buildTypes { release { if (rootProject.file("keystore/release.jks").exists()) signingConfig = signingConfigs.getByName("release") } }

    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}

dependencies {
    // Minimal starting set — Compose BOM + Material 3 + Material Symbols (bundled
    // via compose-material-icons-extended is NOT used; we bundle local vectors in
    // ui/icons to keep the APK lean and offline-friendly). Add deps ONLY when the
    // app's features require them (document the justification in the app repo).
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core") // tiny core set; extended icons come from local bundled vectors
    // kotlinx-coroutines: REQUIRED by the Engine's standing cache-first data
    // layer (com.forgebuild.engine.data.CacheFirstStore) — justified engine-level
    // dependency, do not remove even though the starter UI itself is coroutine-free.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // --- forgehouse50 app dependencies (justified per feature; see BUILD_STATE notes) ---
    implementation("androidx.compose.material:material-icons-extended") // Material Symbols coverage for the full feature set (read/notes/quiz/leaderboard/admin icons)
    implementation("io.ktor:ktor-client-okhttp:2.3.12")                  // REST client against the existing Cloudflare Pages Functions API
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // typed JSON for API DTOs
    implementation("androidx.security:security-crypto:1.1.0-alpha06")     // EncryptedSharedPreferences for the session token (operator requirement)
    implementation("androidx.room:room-runtime:2.6.1")                    // persistent offline store for scripture text + downloaded content index
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")              // audio playback
    implementation("androidx.media3:media3-session:1.4.1")                // MediaSessionService: background playback + media notification
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")                // daily reminder worker + background sync
    implementation("io.coil-kt:coil-compose:2.6.0")                       // load the web repo's avatar illustration PNGs (same asset set as web app)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.navigation:navigation-compose:2.8.0")        // screen navigation
    debugImplementation("androidx.compose.ui:ui-tooling")
}
