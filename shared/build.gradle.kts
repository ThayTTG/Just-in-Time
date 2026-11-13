import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.library")
    id("app.cash.sqldelight")
}

kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    jvm("desktop") { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } } // versão de PC (fase 3) e testes

    sourceSets {
        all { languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi") }
        commonMain.dependencies {
            api(project(":nokylabs-ui"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")
        }
        androidMain.dependencies {
            implementation("app.cash.sqldelight:android-driver:2.0.2")
        }
        val desktopMain by getting {
            dependencies {
                implementation("app.cash.sqldelight:sqlite-driver:2.0.2")
                implementation(compose.desktop.currentOs)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        val desktopTest by getting {
            dependencies { implementation("app.cash.sqldelight:sqlite-driver:2.0.2") }
        }
    }
}

android {
    namespace = "com.nokylabs.justintime.shared"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("JitDatabase") {
            packageName.set("com.nokylabs.justintime.dados")
            srcDirs.setFrom("src/commonMain/sqldelight")
            // Migrações (1.sqm = da versão 1 para a 2): quem já tem o app instalado não perde nada
            verifyMigrations.set(false)
        }
    }
}

// ./gradlew :shared:capturar  → gera imagens das telas (com tarefas de exemplo) em capturas/
tasks.register<JavaExec>("capturar") {
    group = "justintime"
    val compilacao = kotlin.targets.getByName("desktop").compilations.getByName("main")
    dependsOn(compilacao.compileAllTaskName)
    classpath = files(compilacao.output.allOutputs, compilacao.runtimeDependencyFiles)
    mainClass.set("com.nokylabs.justintime.PreviaKt")
    args(rootProject.file("capturas").absolutePath)
}
