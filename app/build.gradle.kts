plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hyperionsoftware.balls"
    compileSdk = 36

    defaultConfig {
        // The Play Store identity - permanent once the app exists on Play Console. Deliberately
        // decoupled from `namespace` above, which is only the Kotlin/R package and never shown
        // to anyone; this one is public (it's in the store URL), so it follows the app's name.
        applicationId = "io.github.aimaicai.balloonsarena"
        minSdk = 26
        targetSdk = 36
        // Bump versionCode by 1 and versionName to the new version for every build that
        // gets uploaded to Play Console - Play Store rejects a re-upload that doesn't
        // increase versionCode over the last one it has, even for the very first release.
        versionCode = 7
        versionName = "1.1.0"
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

// libGDX ships its Android native libraries (.so) inside per-ABI jars rather than an AAR, so
// they're pulled through this separate configuration and unpacked into a build directory that
// is then added as a jniLibs source - the same thing libGDX's own project generator sets up,
// just kept out of src/ so nothing generated ends up committed.
val gdxVersion = "1.14.2"
val natives: Configuration by configurations.creating
val gdxNativesDir = layout.buildDirectory.dir("gdx-natives")

val copyGdxNatives by tasks.registering {
    val nativeJars = natives
    inputs.files(nativeJars)
    outputs.dir(gdxNativesDir)
    doLast {
        val outRoot = gdxNativesDir.get().asFile
        outRoot.deleteRecursively()
        nativeJars.files.forEach { jar ->
            val abi = when {
                jar.name.endsWith("natives-armeabi-v7a.jar") -> "armeabi-v7a"
                jar.name.endsWith("natives-arm64-v8a.jar") -> "arm64-v8a"
                jar.name.endsWith("natives-x86_64.jar") -> "x86_64"
                jar.name.endsWith("natives-x86.jar") -> "x86"
                else -> return@forEach
            }
            copy {
                from(zipTree(jar))
                into(File(outRoot, abi))
                include("*.so")
            }
        }
    }
}

android.sourceSets.getByName("main").jniLibs.srcDir(gdxNativesDir)
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }.configureEach {
    dependsOn(copyGdxNatives)
}

dependencies {
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // Covers the game module (app/src/main/java/.../game) - pure Kotlin, no Android
    // dependencies, so it runs as a plain JVM unit test with no emulator/device needed.
    testImplementation("junit:junit:4.13.2")
}
