# Omni App - ProGuard/R8 Rules

-keep class com.omni.app.** { *; }

-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

-keep class com.arthenica.** { *; }
-dontwarn com.arthenica.**

-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**
-keep class kotlinx.coroutines.android.HandlerContext { *; }
-dontwarn kotlinx.coroutines.**

-keepattributes Signature,Exceptions,*Annotation*,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers class * {
    public <init>(...);
}

-dontobfuscate
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient