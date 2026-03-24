-keepclassmembers class com.autoclicker.claude.data.** { *; }
-keep class com.autoclicker.claude.data.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# Keep AdManager lifecycle callbacks (called by framework)
-keep class com.autoclicker.claude.ads.AdManager { *; }

# Google AdMob
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-dontwarn com.google.android.gms.ads.**
