import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val submissionDemo = providers.gradleProperty("paletteSubmissionDemo").orNull == "true"
val releaseKeys = Properties().apply {
    val propertiesFile = rootProject.file(".signing/release.properties")
    if (propertiesFile.exists()) propertiesFile.inputStream().use { load(it) }
}
if (submissionDemo) require(releaseKeys.isNotEmpty()) {
    "Run scripts/Build-Submission.ps1 to prepare the local signing key."
}

android {
    if (submissionDemo) {
        sourceSets.getByName("release").res.srcDir("src/submission/res")
        sourceSets.getByName("release").manifest.srcFile("src/submission/AndroidManifest.xml")
    }
    testBuildType = providers.gradleProperty("paletteTestBuildType").getOrElse("debug")
    sourceSets.getByName("main").assets.srcDir("../../web/assets")
    buildFeatures { buildConfig = true }
    namespace = "com.palettejourney"
    useLibrary("android.car")
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.palettejourney"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

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
        debug { buildConfigField("boolean", "EMULATOR_DEMO", "true") }
        release {
            isDebuggable = false
            buildConfigField("boolean", "EMULATOR_DEMO", submissionDemo.toString())
            if (submissionDemo) {
                applicationIdSuffix = ".submission"
                versionNameSuffix = "-submission"
                signingConfig = signingConfigs.getByName("submission")
            }
            optimization {
                // Pleos loads Kotlin/SDK classes across processes. Avoid partial R8
                // rewriting in the emulator submission; release is still non-debuggable.
                enable = !submissionDemo
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("com.google.zxing:core:3.5.3")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
