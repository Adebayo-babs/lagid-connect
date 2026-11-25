# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.lagidconnect.wallet.**$$serializer { *; }
-keepclassmembers class com.lagidconnect.wallet.** {
    *** Companion;
}
