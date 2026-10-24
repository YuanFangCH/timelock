plugins { id("com.android.application") }

val releaseKeystorePath = System.getenv("TIMELOCK_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("TIMELOCK_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("TIMELOCK_KEY_ALIAS")
val releaseKeyPassword = System.getenv("TIMELOCK_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "dev.timelock"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.timelock"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.0-preview.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("previewRelease") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("previewRelease")
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
