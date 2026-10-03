import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose) apply false
}

// Export the resolved Gradle graph, rather than parsing build-script text in the test.
val graphFile = layout.buildDirectory.file("architecture/module-graph.tsv")
val exportModuleGraph = tasks.register("exportModuleGraph") {
    outputs.file(graphFile)
    outputs.upToDateWhen { false }
    doLast {
        val rows = subprojects.sortedBy { it.path }.flatMap { module ->
            val android = module.plugins.hasPlugin("com.android.application") ||
                module.plugins.hasPlugin("com.android.library")
            val dependencies = module.configurations.flatMap { it.dependencies }.mapNotNull {
                when (it) {
                    is ProjectDependency -> it.path
                    else -> it.group?.let { group -> "external:$group:${it.name}" }
                }
            }.distinct().sorted()
            listOf("${module.path}\tplugin\t${if (android) "android" else "jvm"}") +
                dependencies.map { "${module.path}\tdependency\t$it" }
        }
        graphFile.get().asFile.apply { parentFile.mkdirs(); writeText(rows.joinToString("\n")) }
    }
}
subprojects {
    tasks.withType<Test>().configureEach {
        if (project.path == ":core:geometry") {
            dependsOn(exportModuleGraph)
            systemProperty("moduleGraph", graphFile.get().asFile.absolutePath)
        }
    }
}
