plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.kurodaki.plimb"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.kurodaki.plimb"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        // La signature release n'est utilisee que si un keystore est fourni via les variables
        // d environnement PLIMB_KEYSTORE / PLIMB_STORE_PASSWORD / PLIMB_KEY_ALIAS / PLIMB_KEY_PASSWORD
        // (voir .github/workflows/android.yml).
        create("release") {
            val keystore = System.getenv("PLIMB_KEYSTORE")
            if (!keystore.isNullOrEmpty() && file(keystore).exists()) {
                storeFile = file(keystore)
                storePassword = System.getenv("PLIMB_STORE_PASSWORD")
                keyAlias = System.getenv("PLIMB_KEY_ALIAS") ?: "plimb"
                keyPassword = System.getenv("PLIMB_KEY_PASSWORD") ?: System.getenv("PLIMB_STORE_PASSWORD")
            }
        }
    }

    val releaseSigningConfigured = signingConfigs.getByName("release").storeFile != null

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig =
                if (releaseSigningConfigured) signingConfigs.getByName("release")
                else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.code.gson:gson:2.10.1")

    // Moteur de rendu Firefox (GeckoView) : ce moteur expose un vrai systeme d extensions
    // WebExtensions, contrairement au WebView systeme.
    implementation("org.mozilla.geckoview:geckoview:141.+")
}
