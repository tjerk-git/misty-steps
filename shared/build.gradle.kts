plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvmToolchain(17)

    androidLibrary {
        namespace = "com.mistysteps.shared"
        compileSdk = 36
        minSdk = 26

        withHostTest {}
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
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
