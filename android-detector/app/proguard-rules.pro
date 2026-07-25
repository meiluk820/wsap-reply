# OkHttp ships its own rules; these silence optional-dependency warnings.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# NotificationListenerService is instantiated by the system, never by our code.
-keep class com.clinic.wanotifybridge.notify.WaNotificationListener { *; }
-keep class com.clinic.wanotifybridge.service.BridgeForegroundService { *; }
