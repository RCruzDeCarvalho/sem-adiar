plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "pt.ricardo.semadiar"
    compileSdk = 34

    defaultConfig {
        applicationId = "pt.ricardo.semadiar"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3"

        buildConfigField("String", "UPDATE_REPO", "\"RCruzDeCarvalho/sem-adiar\"")
        buildConfigField("String", "KOFI", "\"https://ko-fi.com/semlegendas\"")
        buildConfigField("String", "FEEDBACK_EMAIL", "\"ricardoduartecruz@gmail.com\"")
    }

    // Chave fixa para que cada nova versão instale por cima da anterior
    signingConfigs {
        getByName("debug") {
            storeFile = file("keystore.jks")
            storePassword = "semadiar"
            keyAlias = "semadiar"
            keyPassword = "semadiar"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
