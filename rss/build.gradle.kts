import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // The XmlPullParser API ships with Android. On the JVM (tests) kxml2 provides it.
    compileOnly(libs.kxml2)

    testImplementation(libs.kxml2)
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
}
