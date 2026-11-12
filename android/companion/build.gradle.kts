import java.util.Properties

plugins { alias(libs.plugins.android.application) }

val submissionDemo = providers.gradleProperty("paletteSubmissionDemo").orNull == "true"
val releaseKeys = Properties().apply {
    val propertiesFile = rootProject.file(".signing/release.properties")
    if (propertiesFile.exists()) propertiesFile.inputStream().use { load(it) }
}
if (submissionDemo) require(releaseKeys.isNotEmpty()) {
    "Run scripts/Build-Submission.ps1 to prepare the local signing key."
}

android {
    testBuildType = providers.gradleProperty("paletteTestBuildType").getOrElse("debug")
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
    signingConfigs {
        if (releaseKeys.isNotEmpty()) create("submission") {
            storeFile = rootProject.file(releaseKeys.getProperty("storeFile"))
            storePassword = releaseKeys.getProperty("storePassword")
            keyAlias = releaseKeys.getProperty("keyAlias")
            keyPassword = releaseKeys.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isDebuggable = false
            if (submissionDemo) {
                applicationIdSuffix = ".submission"
                versionNameSuffix = "-submission"
                signingConfig = signingConfigs.getByName("submission")
            }
        }
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
