pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TimeTracker"

include(":app")
include(":core:common")
include(":core:database")
include(":core:datastore")
include(":core:location")
include(":core:notifications")
include(":core:testing")
include(":feature:home")
include(":feature:tracking")
include(":feature:reports")
include(":feature:settings")
