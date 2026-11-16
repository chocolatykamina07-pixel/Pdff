# ScanNest ProGuard Rules
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn org.apache.commons.logging.**
-dontwarn org.bouncycastle.**
-keepclassmembers class * {
    @androidx.room.ColumnInfo <fields>;
}
-keep class com.google.mlkit.** { *; }
