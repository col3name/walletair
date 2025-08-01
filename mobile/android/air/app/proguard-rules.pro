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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontwarn javax.annotation.Nullable

-dontwarn org.jetbrains.annotations.**

-keepattributes *Annotation*
-keepclassmembers class your.package.name.** {
    @org.jetbrains.annotations.** <fields>;
    @org.jetbrains.annotations.** <methods>;
}

-keep @com.squareup.moshi.** class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.** <methods>;
}

-keepnames class **JsonAdapter
-keep class **JsonAdapter { *; }

-keep class org.mytonwallet.app_air.walletcore.moshi.** { *; }
-keepclassmembers class * {
    @org.mytonwallet.app_air.walletcore.moshi.** <methods>;
    @org.mytonwallet.app_air.walletcore.moshi.** <fields>;
}

-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @kotlin.Metadata *;
}