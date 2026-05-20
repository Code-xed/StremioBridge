# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /sdk/tools/proguard/proguard-android.txt

# Keep all data classes (used for intent logging/Gson serialization)
-keep class dev.stremiobridge.IntentData { *; }
-keep class dev.stremiobridge.Player { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
