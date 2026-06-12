plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val e2eDebugEnabled =
    providers
        .gradleProperty("timeTracker.e2eDebug")
        .map(String::toBoolean)
        .orElse(false)

android {
    namespace = "com.robertochavez.timetracker.core.location"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        buildConfigField("boolean", "E2E_DEBUG_ENABLED", e2eDebugEnabled.get().toString())
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:logging"))
    implementation(libs.coroutines.android)
    implementation(libs.hilt.android)
    implementation(libs.play.services.location)
    ksp(libs.hilt.compiler)

    testImplementation(project(":core:testing"))
}
