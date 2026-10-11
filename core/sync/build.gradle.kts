plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.robertochavez.timetracker.core.sync"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:logging"))
    implementation(project(":core:location"))
    implementation(libs.coroutines.android)
    implementation(libs.documentfile)
    implementation(libs.hilt.android)
    implementation(libs.room.ktx)
    implementation(libs.room.runtime)
    implementation(libs.work.runtime.ktx)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    ksp(libs.hilt.compiler)
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
