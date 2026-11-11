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
        gradlePluginPortal() // Ausweich-Spiegel, falls Maven Central bremst
    }
}
rootProject.name = "Kalorien-Tracker"
include(":app", ":core")
