# kotlinx.serialization keeps generated serializers via @Serializable; the
# default consumer rules from the library cover them, but we keep our wire
# DTOs explicitly since R8 full mode is on.
-keepclassmembers,allowobfuscation class io.raylytics.justmyweather.data.** {
    *** Companion;
}
-keepclasseswithmembers class io.raylytics.justmyweather.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# InMobi CMP (google-ads-exit Stage B): the SDK's public API and its Gson
# models, which it (de)serializes by reflection. Appodeal and its network
# adapters ship their own consumer rules.
-keep class com.inmobi.cmp.** { *; }
-keep interface com.inmobi.cmp.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.inmobi.cmp.**
