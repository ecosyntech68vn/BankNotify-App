-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod

-keep class com.banknotify.core.model.** { *; }
-keep class com.banknotify.update.UpdateInfo { *; }
-keep class com.banknotify.update.UpdateCheckRequest { *; }
-keep class com.banknotify.update.UpdateCheckResponse { *; }

-keep class com.banknotify.service.server.ApiServer { *; }

-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Ktor references java.management classes for debug detection, unavailable on Android
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
