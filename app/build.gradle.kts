import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)

    id("com.google.dagger.hilt.android")
}

val debugApiBaseUrl = providers
    .gradleProperty("kasirkita.debugApiBaseUrl")
    .orElse("http://10.0.2.2:3000/")
    .get()
val releaseApiBaseUrl = providers
    .gradleProperty("kasirkita.releaseApiBaseUrl")
    .orElse("https://api.kasirkita.invalid/")
    .get()

// Validation: ensure debug URL is valid
val debugUrlUri: URI = try {
    URI(debugApiBaseUrl)
} catch (e: Exception) {
    throw GradleException("Invalid debug API URL '$debugApiBaseUrl': ${e.message}")
}
if (!debugApiBaseUrl.endsWith("/")) {
    throw GradleException("Debug API URL must end with '/': $debugApiBaseUrl")
}
if (debugUrlUri.host == null) {
    throw GradleException("Debug API URL must have a host: $debugApiBaseUrl")
}
val debugScheme = debugUrlUri.scheme?.lowercase()
if (debugScheme !in listOf("http", "https")) {
    throw GradleException("Debug API URL must use http or https: $debugApiBaseUrl")
}

// Validation: ensure release URL is HTTPS and valid
val releaseUrlUri: URI = try {
    URI(releaseApiBaseUrl)
} catch (e: Exception) {
    throw GradleException("Invalid release API URL '$releaseApiBaseUrl': ${e.message}")
}
if (!releaseApiBaseUrl.endsWith("/")) {
    throw GradleException("Release API URL must end with '/': $releaseApiBaseUrl")
}
if (releaseUrlUri.host == null) {
    throw GradleException("Release API URL must have a host: $releaseApiBaseUrl")
}
val releaseScheme = releaseUrlUri.scheme?.lowercase()
if (releaseScheme != "https") {
    throw GradleException("Release API URL must use HTTPS, not $releaseScheme: $releaseApiBaseUrl")
}

// Release signing configuration
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasKeystoreConfig = keystorePropertiesFile.exists()

if (hasKeystoreConfig) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

fun requiredSigningProperty(name: String): String {
    val value = keystoreProperties.getProperty(name)?.trim()
    if (value.isNullOrEmpty()) {
        throw GradleException("Release signing property '$name' must be set in keystore.properties")
    }
    return value
}

val releaseStoreFile = if (hasKeystoreConfig) {
    rootProject.file(requiredSigningProperty("storeFile"))
} else {
    null
}

if (releaseStoreFile != null && !releaseStoreFile.isFile) {
    throw GradleException("Release signing keystore file does not exist: ${releaseStoreFile.absolutePath}")
}

val releaseStorePassword = if (hasKeystoreConfig) requiredSigningProperty("storePassword") else null
val releaseKeyAlias = if (hasKeystoreConfig) requiredSigningProperty("keyAlias") else null
val releaseKeyPassword = if (hasKeystoreConfig) requiredSigningProperty("keyPassword") else null

android {
    namespace = "com.kasirkita.pos"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.kasirkita.pos"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasKeystoreConfig) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"" + debugApiBaseUrl + "\"")
        }

        release {
            buildConfigField("String", "API_BASE_URL", "\"" + releaseApiBaseUrl + "\"")

            if (hasKeystoreConfig) {
                signingConfig = signingConfigs.getByName("release")
            }

            optimization {
                enable = false
            }
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
}

dependencies {

    // Android Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.core.ktx)
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation(libs.androidx.lifecycle.runtime.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)


    // =====================
    // HILT
    // =====================
    implementation("com.google.dagger:hilt-android:2.59.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.4.0")
    implementation("androidx.hilt:hilt-work:1.4.0")
    ksp("com.google.dagger:hilt-compiler:2.59.2")
    ksp("androidx.hilt:hilt-compiler:1.4.0")


    // =====================
    // RETROFIT
    // =====================
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")


    // =====================
    // OKHTTP
    // =====================
    implementation("com.squareup.okhttp3:logging-interceptor:5.1.0")


    // =====================
    // COROUTINES
    // =====================
    implementation(
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2"
    )


    // =====================
    // DATASTORE JWT
    // =====================
    implementation(
        "androidx.datastore:datastore-preferences:1.1.7"
    )


    // =====================
    // ROOM OFFLINE DATABASE
    // =====================
    implementation(
        "androidx.room:room-runtime:2.7.2"
    )

    implementation(
        "androidx.room:room-ktx:2.7.2"
    )

    ksp(
        "androidx.room:room-compiler:2.7.2"
    )


    // =====================
    // VIEWMODEL
    // =====================
    implementation(
        "androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2"
    )


    // =====================
    // NAVIGATION
    // =====================
    implementation(
        "androidx.navigation:navigation-compose:2.9.3"
    )


    // =====================
    // BACKGROUND SYNC
    // =====================
    implementation("androidx.work:work-runtime-ktx:2.11.2")


    // TEST
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
