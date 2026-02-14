plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.fortunateappbuilder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fencewise.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true
    }

    signingConfigs {
        val keystorePropertiesFile = rootProject.file("release.keystore.properties")
        if (keystorePropertiesFile.exists()) {
            create("release") {
                val props = java.util.Properties().apply {
                    keystorePropertiesFile.inputStream().use { load(it) }
                }
                storeFile = rootProject.file(
                    props.getProperty("storeFile")
                        ?: error("storeFile not found in release.keystore.properties")
                )
                storePassword = props.getProperty("storePassword")
                    ?: error("storePassword not found in release.keystore.properties")
                keyAlias = props.getProperty("keyAlias")
                    ?: error("keyAlias not found in release.keystore.properties")
                keyPassword = props.getProperty("keyPassword")
                    ?: error("keyPassword not found in release.keystore.properties")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val releaseConfig = signingConfigs.findByName("release")
            signingConfig = releaseConfig ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20231013")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
