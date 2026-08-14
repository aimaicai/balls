plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hyperionsoftware.balls"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hyperionsoftware.balls"
        minSdk = 26
        targetSdk = 34
        // Bump versionCode by 1 and versionName to the new version for every build that
        // gets uploaded to Play Console - Play Store rejects a re-upload that doesn't
        // increase versionCode over the last one it has, even for the very first release.
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // Fixed, non-secret keystore committed to the repo so CI/debug builds keep a stable
        // signature across runs (needed to reinstall over a previous debug build on device).
        getByName("debug") {
            storeFile = file("ci-debug.keystore")
            storePassword = "ciDebug123"
            keyAlias = "ci-debug"
            keyPassword = "ciDebug123"
        }
        // The real release key is never committed - it's decoded from a GitHub secret at
        // CI time into RELEASE_KEYSTORE_PATH (see .github/workflows/release-aab.yml), or can
        // be pointed at a local keystore the same way for a manual release build. Left
        // entirely unset (rather than pointing at a placeholder) when those env vars are
        // absent, so a plain local `assembleRelease`/`bundleRelease` still succeeds - it just
        // produces an unsigned build, which is fine for local testing.
        create("release") {
            val keystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("RELEASE_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
