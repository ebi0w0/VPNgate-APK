# Proguard rules for GateTunnel VPN
-keepclassmembers class * extends androidx.room.RoomDatabase
-keep class com.vpngate.tunnel.data.local.** { *; }
-keep class com.vpngate.tunnel.data.model.** { *; }
-keepclassmembers class * {
    @androidx.room.Dao *;
    @androidx.room.Entity *;
}
-dontwarn okhttp3.**
-dontwarn okio.**
