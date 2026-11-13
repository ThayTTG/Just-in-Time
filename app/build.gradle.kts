plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nokylabs.justintime.prototipo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nokylabs.justintime.prototipo"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.0.2-prototipo"
    }

    // Chave de depuração fixa, guardada no projeto: assim qualquer computador gera
    // APKs com a mesma assinatura e o celular atualiza sem precisar desinstalar.
    // (Só para o protótipo! O app de verdade terá uma keystore própria, fora do Git.)
    signingConfigs {
        getByName("debug") {
            storeFile = file("prototipo-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
