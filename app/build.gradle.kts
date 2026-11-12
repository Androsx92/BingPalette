plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.bingpalette"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.bingpalette"
        minSdk = 29
        targetSdk = 34
        versionCode = 19
        versionName = "2.4.1"
    }

    // Fixed signing key (from GitHub secrets) so that updates install over older versions.
    // Without the secrets the build falls back to the normal random debug key.
    val ksFile = System.getenv("KEYSTORE_FILE")
    if (ksFile != null) {
        signingConfigs {
            create("fixed") {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")?.trim()
                keyAlias = "bingpalette"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")?.trim()
            }
        }
        buildTypes {
            getByName("debug") {
                signingConfig = signingConfigs.getByName("fixed")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.palette:palette:1.0.0")
}
