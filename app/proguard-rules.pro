# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# ============================================
# Source file and line number preservation
# Useful for debugging stack traces in crash reports
# ============================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================
# GSON serialization classes
# ============================================
-keep class io.github.trojan_gfw.igniter.TrojanURLParseResult { *; }
-keep class io.github.trojan_gfw.igniter.TrojanConfig { *; }

# ============================================
# AIDL interfaces
# Keep all AIDL-generated classes and interfaces
# ============================================
-keep class io.github.trojan_gfw.igniter.proxy.aidl.ITrojanService { *; }
-keep class io.github.trojan_gfw.igniter.proxy.aidl.ITrojanService$* { *; }
-keep class io.github.trojan_gfw.igniter.proxy.aidl.ITrojanServiceCallback { *; }
-keep class io.github.trojan_gfw.igniter.proxy.aidl.ITrojanServiceCallback$* { *; }

# ============================================
# ViewModel classes
# Keep ViewModel classes to ensure proper lifecycle management
# ============================================
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class io.github.trojan_gfw.igniter.main.MainViewModel { *; }
-keep class io.github.trojan_gfw.igniter.main.MainUiState { *; }

# ============================================
# Native methods
# Keep native method names for JNI
# ============================================
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================
# Parcelable classes
# Keep Parcelable CREATOR fields
# ============================================
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
