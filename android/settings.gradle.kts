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

rootProject.name = "simplicity-android"

// Mirrors the ten iOS packages. Only the modules the first slice needs exist yet; :learn,
// :reflect, :assistant and :admin arrive with their own slices.
include(":app")
include(":api-client")
include(":foundation")
include(":api")
include(":design")
include(":services")
include(":auth")
include(":learn")
include(":reflect")
include(":assistant")
include(":admin")
include(":testing")
