plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

compose.resources {
    packageOfResClass = "com.mistysteps.app.resources"
    publicResClass = true
    // values/ and values-nl/ in strings/ are also an Android res directory for :app, so R.string and Res.string agree.
    customDirectory("commonMain", provider { layout.projectDirectory.dir("strings") })
}

kotlin {
    jvmToolchain(17)

    androidLibrary {
        namespace = "com.mistysteps.shared"
        compileSdk = 36
        minSdk = 26

        withHostTest {}

        // Without this the Compose resources (the shared strings) are not packaged into the Android app.
        androidResources { enable = true }
    }

    // iOS targets, exported as the static framework "Shared" for the Xcode app.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    // Runs the shared tests as native code on this Mac, no simulator needed.
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            // Pure logic only: no Android or JVM APIs in here.
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            api(compose.components.resources)
            api("org.jetbrains.compose.material:material-icons-core:1.7.3")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.11.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
