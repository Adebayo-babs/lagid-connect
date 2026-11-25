# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.lagidconnect.verifier.**$$serializer { *; }
-keepclassmembers class com.lagidconnect.verifier.** {
    *** Companion;
}
