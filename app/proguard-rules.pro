# Ktor, kotlinx.serialization, Room, Hilt and Media3 ship consumer rules.

# Navigation routes and DTOs are looked up by their generated serializers.
-keep @kotlinx.serialization.Serializable class dev.podwave.** { *; }

# Ktor references optional SLF4J classes that are not present on Android.
-dontwarn org.slf4j.impl.StaticLoggerBinder

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
