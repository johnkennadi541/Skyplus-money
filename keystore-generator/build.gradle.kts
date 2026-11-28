plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.skyplus.keystore"
    compileSdk { version = release(36) { minorApiLevel = 1 } }

    defaultConfig {
        applicationId = "com.skyplus.keystore"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-prototype"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.83")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.83")
    testImplementation("junit:junit:4.13.2")
}
