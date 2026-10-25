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
        versionCode = 5
        versionName = "1.5"
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
