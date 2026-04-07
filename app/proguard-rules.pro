# === Data models (serialized with Gson) ===
-keepclassmembers class com.autoclicker.claude.data.** { *; }
-keep class com.autoclicker.claude.data.** { *; }

# === Gson ===
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# === AdManager (lifecycle callbacks called by framework) ===
-keep class com.autoclicker.claude.ads.AdManager { *; }

# === Google AdMob ===
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# === AccessibilityService (instantiated by system) ===
-keep class com.autoclicker.claude.service.AutoClickService { *; }
-keep class com.autoclicker.claude.service.TapForegroundService { *; }

# === Compose ===
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# === Kotlin Coroutines ===
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { *; }

# === ViewModel history entry (used in UI) ===
-keep class com.autoclicker.claude.ui.HistoryEntry { *; }

# === General Android ===
-keepclassmembers class * implements android.os.Parcelable { *; }
-keepclassmembers class * implements java.io.Serializable { *; }
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
