pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository { maven { url = uri("third_party/maven") } }
            filter { includeGroup("com.aiphotographer.thirdparty") }
        }
        google(); mavenCentral()
    }
}
rootProject.name = "AI Photographer"
include(":app", ":core:model", ":core:geometry", ":feature:camera")
include(":perception:api", ":core:photography")
include(":perception:mediapipe")
