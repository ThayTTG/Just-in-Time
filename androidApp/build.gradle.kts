plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

android {
    namespace = "com.nokylabs.justintime.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nokylabs.justintime"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0-alpha.1"
    }

    // Chave de teste fixa (a mesma em qualquer PC): o celular atualiza sem desinstalar.
    // A versão publicada terá uma keystore própria, guardada FORA do Git.
    signingConfigs {
        getByName("debug") {
            storeFile = file("justintime-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.9.3")
}
