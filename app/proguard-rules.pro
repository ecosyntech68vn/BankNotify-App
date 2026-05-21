-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

-keep class com.banknotify.core.model.** { *; }
-keep class com.banknotify.update.UpdateInfo { *; }

-keep class com.banknotify.service.server.ApiServer { *; }

-keepattributes Signature
-keepattributes *Annotation*

-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
