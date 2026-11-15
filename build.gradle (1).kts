plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

// Every GitHub Actions run gets a higher versionCode, so each new APK installs
// as an UPDATE over the previous one (all app data is kept).
val ciRunNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    // Kept identical to the original project so the new APK updates an already-installed copy.
    applicationId = "com.aistudio.ekambani.system"
    minSdk = 24
    targetSdk = 36
    versionCode = ciRunNumber
    versionName = "1.0.$ciRunNumber"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      // CI passes these through environment variables (see .github/workflows/build-apk.yml).
      storeFile = file(System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks")
      storePassword = System.getenv("STORE_PASSWORD") ?: "password123"
      keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
      keyPassword = System.getenv("KEY_PASSWORD") ?: "password123"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Debug builds use the standard Android debug key automatically.
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  lint {
    // Lint must never be the reason an APK is not produced.
    abortOnError = false
    checkReleaseBuilds = false
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.swiperefreshlayout)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
}
