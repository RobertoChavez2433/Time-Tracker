plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val e2eDebugEnabled =
    providers
        .gradleProperty("timeTracker.e2eDebug")
        .map(String::toBoolean)
        .orElse(false)

val compatibleDebugSigningEnabled =
    providers
        .gradleProperty("timeTracker.compatDebugSigning")
        .map(String::toBoolean)
        .orElse(false)

val defaultMapsApiKey =
    providers
        .gradleProperty("timeTracker.mapsApiKey")
        .orElse(providers.environmentVariable("TIME_TRACKER_MAPS_API_KEY"))
        .orElse("UNCONFIGURED")
val debugMapsApiKey =
    providers
        .gradleProperty("timeTracker.mapsApiKey.debug")
        .orElse(providers.environmentVariable("TIME_TRACKER_MAPS_API_KEY_DEBUG"))
        .orElse(defaultMapsApiKey)
val releaseMapsApiKey =
    providers
        .gradleProperty("timeTracker.mapsApiKey.release")
        .orElse(providers.environmentVariable("TIME_TRACKER_MAPS_API_KEY_RELEASE"))
        .orElse(defaultMapsApiKey)

val releaseSigningPropertiesFile = file("${System.getProperty("user.home")}/.android/time-tracker-release.properties")
val releaseSigningProperties =
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile
            .readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
            .associate { line -> line.substringBefore("=") to line.substringAfter("=") }
    } else {
        emptyMap()
    }
val hasReleaseSigningProperties = releaseSigningProperties.isNotEmpty()

android {
    namespace = "com.robertochavez.timetracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.robertochavez.timetracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.2.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = defaultMapsApiKey.get()
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        if (hasReleaseSigningProperties) {
            create("release") {
                storeFile = file(requireNotNull(releaseSigningProperties["storeFile"]))
                storePassword = requireNotNull(releaseSigningProperties["storePassword"])
                keyAlias = requireNotNull(releaseSigningProperties["keyAlias"])
                keyPassword = requireNotNull(releaseSigningProperties["keyPassword"])
            }
        }
    }

    buildTypes {
        debug {
            // Device verification can coexist with the user's installed debug and release apps.
            applicationIdSuffix = providers.gradleProperty("timeTracker.debugApplicationIdSuffix").orElse(".debug").get()
            manifestPlaceholders["MAPS_API_KEY"] = debugMapsApiKey.get()
            buildConfigField("boolean", "E2E_DEBUG_ENABLED", e2eDebugEnabled.get().toString())
        }
        release {
            manifestPlaceholders["MAPS_API_KEY"] = releaseMapsApiKey.get()
            when {
                compatibleDebugSigningEnabled.get() -> signingConfig = signingConfigs.getByName("debug")
                hasReleaseSigningProperties -> signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("boolean", "E2E_DEBUG_ENABLED", "false")
        }
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
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:logging"))
    implementation(project(":core:location"))
    implementation(project(":core:notifications"))
    implementation(project(":core:sync"))
    implementation(project(":feature:home"))
    implementation(project(":feature:tracking"))
    implementation(project(":feature:reports"))
    implementation(project(":feature:settings"))

    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.coroutines.android)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.room.runtime)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.coroutines.test)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation("androidx.test:runner:1.6.2")
}
