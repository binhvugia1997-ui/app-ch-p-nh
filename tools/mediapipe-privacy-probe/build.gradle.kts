plugins { id("com.android.application") version "9.4.1" }

android {
    namespace = "com.aiphotographer.audit"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.aiphotographer.audit"
        minSdk = 24
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += "transport"
    productFlavors {
        create("included") {
            dimension = "transport"
            buildConfigField("boolean", "EXCLUDED", "false")
        }
        create("excluded") {
            dimension = "transport"
            buildConfigField("boolean", "EXCLUDED", "true")
        }
    }
    buildFeatures { buildConfig = true }
    sourceSets.getByName("main").assets.srcDir(
        providers.gradleProperty("probeAssets").getOrElse("../../device-evidence/phase2/probe-assets")
    )
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    "includedImplementation"("com.google.mediapipe:tasks-vision:0.10.32")
    "excludedImplementation"("com.google.mediapipe:tasks-vision:0.10.32") {
        exclude(group = "com.google.android.datatransport")
        exclude(group = "com.google.firebase")
    }
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}

tasks.register("exportAuditGraph") {
    doLast {
        for (variant in listOf("included", "excluded")) {
            val configuration = configurations.getByName("${variant}DebugRuntimeClasspath")
            val graph = configuration.incoming.resolutionResult.allDependencies
                .map { "${it.from.id.displayName} -> ${it.requested.displayName}" }.sorted()
            val output = layout.buildDirectory.file("audit/${variant}-graph.txt").get().asFile
            output.parentFile.mkdirs()
            output.writeText(graph.joinToString("\n"))
            val components = configuration.resolvedConfiguration.resolvedArtifacts
                .map { "${it.moduleVersion.id}\t${it.file.name}" }.sorted()
            output.resolveSibling("${variant}-components.tsv").writeText(components.joinToString("\n"))
        }
    }
}
