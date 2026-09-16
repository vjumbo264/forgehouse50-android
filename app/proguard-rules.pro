# Engine defaults. Compose needs no special rules with R8 full mode off defaults.
-dontwarn org.jetbrains.annotations.**
-keepclassmembers class * implements android.os.Parcelable { public static final android.os.Parcelable$Creator *; }
# forgehouse50: slf4j-api is pulled in transitively (via ktor-client-okhttp) but
# ships no logger backend on Android; these classes are referenced reflectively
# from org.slf4j.LoggerFactory and are optional at runtime.
-dontwarn org.slf4j.**

# leaderboard_audio_removal_offline_bible_v1: removing Media3 pulled the
# transitive com.google.errorprone:annotations artifact off the R8 classpath,
# but Tink (via androidx security-crypto EncryptedSharedPreferences) still
# references these annotation types. They are annotations only — safe to ignore.
-dontwarn com.google.errorprone.annotations.**
