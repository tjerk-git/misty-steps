import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Single source of the version: version.properties (bumped by tools/release.sh).
val version = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }

// ./gradlew ... -Pstore=play builds the Google Play version, which sells the magic eraser through Play Billing.
// Every other build (F-Droid, debug, release) has no store: no eraser button and no billing code in the APK.
val playStore = findProperty("store") == "play"

android {
    namespace = "com.mistysteps.app"
    compileSdk = 36

    sourceSets {
        // The strings live in :shared so Compose screens on every platform read the same text.
        getByName("main").res.srcDir("../shared/strings")
        // One of these two folders joins the build: they hold the only code that differs (src/*/java/.../eraser/StoreHook.kt).
        getByName("main").java.srcDir(if (playStore) "src/play/java" else "src/fdroid/java")
    }

    defaultConfig {
        // The id people install. Never change it after the first public release.
        // (The code package, `namespace` above, can stay as it is.)
        applicationId = "com.potloodgum.mistysteps"
        minSdk = 26
        targetSdk = 36
        versionCode = version.getProperty("versionCode").toInt()
        versionName = version.getProperty("versionName")
        buildConfigField("String", "STORE", if (playStore) "\"play\"" else "\"fdroid\"")

        // The F-Droid repo is a Cloudflare Worker with a 25 MiB limit per file, and the MapLibre native
        // library is 8-12 MB per CPU type. tools/release.sh passes -PfdroidAbi=arm64-v8a so that APK carries
        // only 64-bit ARM. Other builds (Play Store, debug, emulators) keep every CPU type.
        (findProperty("fdroidAbi") as String?)?.let { abi -> ndk { abiFilters += abi } }
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
            // ./gradlew installDebug -PminifyDebug runs the same R8 shrinking and obfuscation as the release, to test the keep rules
            // on a device without touching the release app (the release has the same app id and key as the real install).
            if (project.hasProperty("minifyDebug")) {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            }
        }
        release {
            resValue("string", "app_name", "Misty Steps")
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // Only for moving data into a release install by hand (run-as needs a debuggable app):
            // ./gradlew assembleRelease -PdebuggableRelease. tools/release.sh refuses such builds.
            isDebuggable = project.hasProperty("debuggableRelease")
            // R8: shrink, optimize and obfuscate the code, and drop unused resources. Play vitals ("DEX code optimization")
            // warns when this is off. Keep rules live in proguard-rules.pro, and only for what really needs them.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    // The map: MapLibre Native, drawing the downloaded province packs.
    implementation(project(":shared"))
    implementation("org.maplibre.gl:android-sdk:11.8.0")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.collection:collection:1.5.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    if (playStore) implementation("com.android.billingclient:billing-ktx:8.0.0")

    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for parsing tests (Android's copy is only a stub on the JVM).
    testImplementation("org.json:json:20240303")
}
