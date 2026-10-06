pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "AI Photographer"
include(":app", ":core:model", ":core:geometry", ":feature:camera")
include(":perception:api", ":core:photography")
