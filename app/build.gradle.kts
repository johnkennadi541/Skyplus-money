import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    // applicationId never changes. versionCode strictly increases for every distributed
    // APK so an update installs over the previous build: 1 was the throw-away-keystore
    // debug build already installed on the test phone, so the first stable-keystore APK
    // is 2. Increment by one before each future distribution.
    applicationId = "com.skyplus.money"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    // Debug signing. CI supplies ONE stable debug keystore through environment
    // variables so every debug APK keeps the same SHA-1/SHA-256 and Google Sign-In
    // stays registered.
    //
    // The four CI variables are all-or-nothing. If ANY of them is supplied, ALL FOUR must
    // be non-blank and the keystore must be an existing file; otherwise configuration
    // fails here, immediately, listing only the missing variable NAMES. CI credentials
    // are never mixed with the local fallback credentials.
    //
    // Only when none of the four is supplied do the local defaults apply
    // (git-ignored ./debug.keystore with android / androiddebugkey / android), which is
    // what local and AI Studio builds rely on.
    //
    // No secret value is printed, logged, or written to BuildConfig, resources or
    // APK metadata.
    create("debugConfig") {
      val ciNames =
        listOf(
          "SKYPLUS_DEBUG_KEYSTORE_PATH",
          "SKYPLUS_DEBUG_STORE_PASSWORD",
          "SKYPLUS_DEBUG_KEY_ALIAS",
          "SKYPLUS_DEBUG_KEY_PASSWORD",
        )
      val ciValues = ciNames.associateWith { System.getenv(it) }
      val supplied = ciValues.filterValues { !it.isNullOrBlank() }

      if (supplied.isEmpty()) {
        // No CI signing at all: unchanged local / AI Studio behaviour.
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      } else {
        val missing = ciNames.filterNot { supplied.containsKey(it) }
        if (missing.isNotEmpty()) {
          throw GradleException(
            "Debug signing is only partly configured. Set all four of " +
              "SKYPLUS_DEBUG_KEYSTORE_PATH, SKYPLUS_DEBUG_STORE_PASSWORD, " +
              "SKYPLUS_DEBUG_KEY_ALIAS and SKYPLUS_DEBUG_KEY_PASSWORD, or none of them. " +
              "Missing: ${missing.joinToString(", ")}"
          )
        }
        val keystore = file(supplied.getValue("SKYPLUS_DEBUG_KEYSTORE_PATH")!!)
        if (!keystore.isFile) {
          throw GradleException(
            "SKYPLUS_DEBUG_KEYSTORE_PATH does not point to an existing file: ${keystore.path}"
          )
        }
        storeFile = keystore
        storePassword = supplied.getValue("SKYPLUS_DEBUG_STORE_PASSWORD")
        keyAlias = supplied.getValue("SKYPLUS_DEBUG_KEY_ALIAS")
        keyPassword = supplied.getValue("SKYPLUS_DEBUG_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
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
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.androidx.hilt.work)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  ksp(libs.androidx.hilt.compiler)
  implementation(libs.kotlinx.serialization.json)
  // Phase 2+ Room & SQLCipher (enabled for Phase 2 Core Finance)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  ksp(libs.androidx.room.compiler)
  implementation(libs.sqlcipher.android)
  implementation(libs.androidx.sqlite)
  // implementation(libs.coil.compose)
  // Phase 1+ Networking & Firebase (enable in Phase 1+ / Phase 5)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // "ksp"(libs.moshi.kotlin.codegen)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.hilt.android.testing)
  kspTest(libs.hilt.compiler)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
