import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing keys are read from keystore.properties at the repo root (git-ignored;
// see keystore.properties.example). When the file is absent the release APK is left
// unsigned - exactly as it was before this was wired - so debug builds and CI are
// unaffected. CI / env-var signing can be added when a release workflow exists.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.ikasle.scrollkill"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.ikasle.scrollkill"
        minSdk = 24
        targetSdk = 37
        // versionCode is monotonic - bump it on every store upload, never reuse a value.
        // versionName is the user-facing semver.
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 (code + resource shrinking) stays OFF until a minified release APK
            // has been smoke-tested on a device: checklist E4. To enable, set
            // `enable = true` and verify against the real app - the accessibility
            // service binds and detects, BlockingEngine fires GLOBAL_ACTION_BACK,
            // Settings round-trips persist, session history writes and prunes -
            // then add any missing keeps to proguard-rules.pro.
            optimization {
                enable = false
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Assigned only when keystore.properties is present; otherwise the
            // release APK is left unsigned, unchanged from before Session 11.
            signingConfig = signingConfigs.getByName("release").takeIf { hasReleaseKeystore }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
    sourceSets {
        // MigrationTestHelper reads the exported schema JSONs from the androidTest APK assets.
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

// Room exports the schema so migrations can be added and tested later.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    // Compose UI tests run as Robolectric unit tests (no device); needs the BOM + finder API
    // on the unit-test classpath. ui-test-manifest already reaches testDebug via debugImplementation.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}