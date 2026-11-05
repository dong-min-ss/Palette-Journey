plugins { alias(libs.plugins.android.application) }

android {
    buildFeatures { buildConfig = true }
    namespace = "com.palettejourney.companion"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.palettejourney.companion"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "0.1-mvp"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
