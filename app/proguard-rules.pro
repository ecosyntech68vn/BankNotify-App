-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

-keep class com.banknotify.core.model.** { *; }
-keep class com.banknotify.update.UpdateInfo { *; }

-keep class com.banknotify.service.server.ApiServer { *; }

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod

-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
