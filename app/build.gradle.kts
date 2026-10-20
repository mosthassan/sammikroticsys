import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.FileInputStream
import java.util.Locale
import java.util.Properties

// Force US Locale and UTF-8 encoding so KSP and Room code generators on Arabic Windows
// generate ASCII numeric literals (e.g., RoomOpenDelegate(3, ...)) rather than Arabic-Indic numerals (RoomOpenDelegate(٣, ...)).
Locale.setDefault(Locale.US)
System.setProperty("user.language", "en")
System.setProperty("user.country", "US")
System.setProperty("file.encoding", "UTF-8")

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
  FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
}

val releaseStoreFilePath: String? = keystoreProperties.getProperty("storeFile") ?: System.getenv("KEYSTORE_PATH")
val releaseStorePassword: String? = keystoreProperties.getProperty("storePassword") ?: System.getenv("STORE_PASSWORD")
val releaseKeyAlias: String? = keystoreProperties.getProperty("keyAlias") ?: System.getenv("KEY_ALIAS") ?: "upload"
val releaseKeyPassword: String? = keystoreProperties.getProperty("keyPassword") ?: System.getenv("KEY_PASSWORD")

val hasReleaseSigning: Boolean = !releaseStoreFilePath.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank() &&
    file(releaseStoreFilePath).exists()

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.samtecai.sammikrotic"
    minSdk = 24
    targetSdk = 36
    val vCode = (project.findProperty("VERSION_CODE") as? String)?.toIntOrNull() ?: 1
    val vName = (project.findProperty("VERSION_NAME") as? String) ?: "1.0.0"
    versionCode = vCode
    versionName = vName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = file(releaseStoreFilePath!!)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    debug {
      // Uses default Android debug signing configuration. No custom keys required.
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

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.withType<JavaCompile>().configureEach {
  options.encoding = "UTF-8"
}

tasks.matching { it.name.startsWith("ksp") }.configureEach {
  doFirst {
    Locale.setDefault(Locale.US)
    System.setProperty("user.language", "en")
    System.setProperty("user.country", "US")
    System.setProperty("file.encoding", "UTF-8")
  }
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
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Firestore for cloud sync
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.crashlytics)

  // Firebase Auth and Google Sign-In via Credential Manager
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.play.services.auth)
  implementation(libs.firebase.appcheck.recaptcha)
  debugImplementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

tasks.matching { it.name.contains("bundleRelease", ignoreCase = true) }.configureEach {
  doFirst {
    if (!hasReleaseSigning) {
      val missing = buildList {
        if (releaseStoreFilePath.isNullOrBlank() || !file(releaseStoreFilePath).exists()) add("storeFile (${releaseStoreFilePath ?: "not set"})")
        if (releaseStorePassword.isNullOrBlank()) add("storePassword")
        if (releaseKeyPassword.isNullOrBlank()) add("keyPassword")
      }
      val warningMsg = """
        |==========================================================================================
        |⚠️  [BUNDLE RELEASE SIGNING WARNING]
        |Release signing configuration is incomplete or missing. Missing: ${missing.joinToString(", ")}.
        |To sign release bundles, define 'keystore.properties' in rootDir or set KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD.
        |bundleRelease will proceed generating an unsigned release bundle.
        |==========================================================================================
      """.trimMargin()
      println(warningMsg)
      logger.warn(warningMsg)
    }
  }
}
