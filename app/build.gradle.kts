plugins {
    id("com.android.application")
}

// Номер версии растёт со временем, поэтому любая новая сборка (своя или с GitHub) ставится поверх старой
val buildMinutes = ((System.currentTimeMillis() / 1000L - 1767225600L) / 60L).toInt()
val runNumber = System.getenv("GITHUB_RUN_NUMBER")

android {
    namespace = "com.anderza.fmnet"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.anderza.fmnet"
        minSdk = 21
        targetSdk = 34
        versionCode = buildMinutes
        versionName = if (runNumber != null) "1.0.$runNumber" else "1.0.local"
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
