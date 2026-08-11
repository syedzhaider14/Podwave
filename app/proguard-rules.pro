# kotlinx.serialization (navigation routes and network DTOs)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class app.podwave.** { *** Companion; }
-keepclasseswithmembers class app.podwave.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class app.podwave.**$$serializer { *; }

# Ktor references optional JVM-only classes that don't exist on Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**

# XmlPullParser is provided by the Android framework.
-dontwarn org.xmlpull.v1.**
