plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.loveletter"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.loveletter"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SERVER_URL", "\"https://api.your-domain.com\"")
        buildConfigField("String", "API_KEY", "\"DEBUG_API_KEY\"")
    }

    buildTypes {
        getByName("release") {
            optimization {
                enable = false
            }
            val prodApiKey = System.getenv("PRODUCTION_API_KEY")
            if (!prodApiKey.isNullOrBlank()) {
                buildConfigField("String", "API_KEY", "\"$prodApiKey\"")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.ui)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.io.ktor.ktor.client.core)
    implementation(libs.io.ktor.ktor.client.okhttp)
    implementation(libs.io.ktor.ktor.client.content.negotiation)
    implementation(libs.io.ktor.ktor.serialization.kotlinx.json)
    implementation(libs.io.ktor.ktor.client.logging)
    implementation(libs.okhttp)
    implementation(platform("com.google.firebase:firebase-bom:34.14.1"))
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.serialization.json)
}
