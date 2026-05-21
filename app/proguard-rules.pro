-keep class fi.iki.elonen.** { *; }
-dontwarn fi.iki.elonen.**

-keepclassmembers class com.banknotify.core.model.** { *; }
-keepclassmembers class com.banknotify.update.UpdateInfo** { *; }

-keep class com.banknotify.service.server.ApiServer { *; }

-keepattributes Signature
-keepattributes *Annotation*
