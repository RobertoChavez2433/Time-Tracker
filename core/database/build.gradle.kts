plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.robertochavez.timetracker.core.database"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        getByName("test").assets.srcDir("$projectDir/schemas")
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
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
    implementation(libs.coroutines.android)
    implementation(libs.hilt.android)
    implementation(libs.room.ktx)
    implementation(libs.room.runtime)
    ksp(libs.hilt.compiler)
    ksp(libs.room.compiler)

    testImplementation(libs.androidx.test.core)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
}
