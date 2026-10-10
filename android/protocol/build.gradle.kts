import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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

// The JVM bindings carry desktop native libraries, so the protocol tests run on the Mac.
// The app swaps them for the Android bindings, which have the same API.
dependencies {
    implementation(libs.nostr.sdk.jvm)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.zxing.core)
}

// Publishes a test location for one watching phone from a throwaway key kept under build/.
tasks.register<JavaExec>("fakePublish") {
    description = "Publishes a test location: -PfakeArgs=\"<reader invite> <lat> <lon> [relay ...]\""
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "app.mismeet.protocol.FakePublisherKt"
    args = (project.findProperty("fakeArgs") as String?)?.trim()?.split(Regex("\\s+")) ?: emptyList()
}

tasks.test {
    useJUnit()
}
