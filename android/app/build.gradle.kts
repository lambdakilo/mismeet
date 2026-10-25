import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.mismeet.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.mismeet.android"
        minSdk = 31
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.0"
        // Both test phones and the Apple silicon emulator are arm64; the other ABIs would
        // triple the APK through the bindings' native libraries.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // The release key stays out of git: android/keystore.properties names it, as the README
    // describes. Without that file there is no release signing and the release build fails.
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        val keystore = Properties().apply { keystoreFile.inputStream().use(::load) }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
        buildTypes {
            getByName("release") {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Play cannot tell an app which track installed it, so closed testing gets builds of its own
    // with -PtestBuild, the only release builds that show the log.
    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "SHOWS_LOG", "true")
        }
        getByName("release") {
            buildConfigField("boolean", "SHOWS_LOG", providers.gradleProperty("testBuild").isPresent.toString())
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
    }
}

// A store build must come from its tag, see scripts/release.sh. -PuntaggedRelease skips the
// check for trying out the signing setup; such an APK must never be published.
val checkReleaseTag by tasks.registering {
    val script = rootProject.file("../scripts/release.sh")
    val skip = providers.gradleProperty("untaggedRelease").isPresent
    doLast {
        if (skip) return@doLast
        val process = ProcessBuilder(script.path, "check", "android").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() != 0) throw GradleException(output)
    }
}
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(checkReleaseTag)
}

dependencies {
    implementation(project(":protocol")) {
        exclude(group = "org.nostrdevkit", module = "nostr-sdk-jvm")
        exclude(group = "net.java.dev.jna")
    }
    implementation(libs.nostr.sdk.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
    implementation(libs.osmdroid.android)
    testImplementation(libs.junit)
}
