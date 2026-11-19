pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // Mozilla's Maven repo (Android Components + GeckoView).
        maven("https://maven.mozilla.org/maven2/")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.mozilla.org/maven2/")
    }
}
rootProject.name = "ZenSync MVP"
include(":app")
