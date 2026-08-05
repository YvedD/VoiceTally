# VoiceTally ProGuard/R8 Rules

# --- FastExcel Reader Support ---
# Lightweight and doesn't require complex rules, but we keep the package to be safe
-keep class org.dhatim.fastexcel.** { *; }

# FastExcel depends on StAX (javax.xml.stream) which is not fully present on Android.
# We added 'stax-api' dependency, and we must keep these classes.
-keep class javax.xml.stream.** { *; }
-dontwarn javax.xml.stream.**

# --- Room Database ---
-keep class * extends androidx.room.RoomDatabase
-keep class com.yvesds.vt5.core.database.entities.** { *; }
-keep interface com.yvesds.vt5.core.database.dao.** { *; }

# --- Vico Charts ---
-keep class com.patrykandpatrick.vico.** { *; }

# General optimizations
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
