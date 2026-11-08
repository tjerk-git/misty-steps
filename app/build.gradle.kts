import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Single source of the version: version.properties (bumped by tools/release.sh).
val version = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }

android {
    namespace = "com.mistysteps.app"
    compileSdk = 36

    defaultConfig {
        // The id people install. Never change it after the first public release.
        // (The code package, `namespace` above, can stay as it is.)
        applicationId = "com.potloodgum.mistysteps"
        minSdk = 26
        targetSdk = 36
        versionCode = version.getProperty("versionCode").toInt()
        versionName = version.getProperty("versionName")
    }

    // Release signing reads ../keystore.properties (kept out of git). Without it, release
    // builds come out unsigned and debug builds are unaffected.
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        val keys = Properties().apply { keystoreFile.inputStream().use { load(it) } }
        signingConfigs {
            create("release") {
                storeFile = file(keys.getProperty("storeFile"))
                storePassword = keys.getProperty("storePassword")
                keyAlias = keys.getProperty("keyAlias")
                keyPassword = keys.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Your local build: installs next to the F-Droid release as "Misty Steps Dev",
        // with its own data, and keeps the battery logging.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "Misty Steps Dev")
        }
        release {
            resValue("string", "app_name", "Misty Steps")
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // Only for moving data into a release install by hand (run-as needs a debuggable app):
            // ./gradlew assembleRelease -PdebuggableRelease. tools/release.sh refuses such builds.
            isDebuggable = project.hasProperty("debuggableRelease")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.collection:collection:1.5.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.activity:activity-compose:1.11.0")

    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for parsing tests (Android's copy is only a stub on the JVM).
    testImplementation("org.json:json:20240303")
}
