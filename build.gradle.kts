plugins {
    id("com.android.application") version "9.4.0" apply false
    // AGP 9 has Kotlin support BUILT IN — org.jetbrains.kotlin.android must NOT be
    // declared (hard-fails: "no longer required for Kotlin support since AGP 9.0").
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
}
