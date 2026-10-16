plugins {
    id("com.android.application")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.anderza.fmnet"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.anderza.fmnet"
        minSdk = 21
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    signingConfigs {
        create("release") {
            // Ключ лежит в репозитории, чтобы каждая сборка устанавливалась поверх предыдущей.
            // Для публикации в магазинах замените его своим (см. README).
            storeFile = file("release.keystore")
            storePassword = System.getenv("RADIONET_STORE_PASSWORD")?.takeIf { it.isNotBlank() } ?: "radionet"
            keyAlias = System.getenv("RADIONET_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "radionet"
            keyPassword = System.getenv("RADIONET_KEY_PASSWORD")?.takeIf { it.isNotBlank() } ?: "radionet"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
