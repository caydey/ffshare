# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line numbers for debugging crashes
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep all annotations
-keepattributes *Annotation*

# Keep Parcelable implementation
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep Service and BroadcastReceiver classes
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Application

# Keep all classes in the main package
-keep class com.caydey.ffshare.** { *; }

# Preserve sealed classes and their subclasses
-keep class com.caydey.ffshare.service.EncodingState { *; }
-keep class com.caydey.ffshare.service.EncodingState$** { *; }

# Keep all data classes (used for state management)
-keepclassmembers class com.caydey.ffshare.service.EncodingState$* {
    <fields>;
    <methods>;
}

# Keep Log class
-keep class com.caydey.ffshare.utils.logs.Log { *; }

# FFmpeg Kit - Critical for native library interaction
-keep class com.antonkarpenko.ffmpegkit.** { *; }
-keep interface com.antonkarpenko.ffmpegkit.** { *; }
-dontwarn com.antonkarpenko.ffmpegkit.**

# Keep native methods
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Timber logging library
-dontwarn org.jetbrains.annotations.**
-keep class timber.log.** { *; }

# Kotlin specific
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    fun getKind();
    fun getMetadataVersion();
    fun getBytecodeVersion();
    fun getData1();
    fun getData2();
    fun getExtraString();
    fun getPackageName();
    fun getExtraInt();
}
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkExpressionValueIsNotNull(...);
    public static void checkNotNullExpressionValue(...);
    public static void checkParameterIsNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkFieldIsNotNull(...);
    public static void checkReturnedValueIsNotNull(...);
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# AndroidX and Material
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# Keep ViewBinding classes
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** bind(android.view.View);
    public static *** inflate(android.view.LayoutInflater);
}

# Enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}