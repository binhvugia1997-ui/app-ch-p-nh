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
            buildConfigField("boolean", "TELEMETRY_EXPECTED", "true")
        }
        create("excluded") {
            dimension = "transport"
            buildConfigField("boolean", "EXCLUDED", "true")
            buildConfigField("boolean", "TELEMETRY_EXPECTED", "false")
        }
        create("upstream") {
            dimension = "transport"
            applicationIdSuffix = ".upstream"
            buildConfigField("boolean", "EXCLUDED", "false")
            buildConfigField("boolean", "TELEMETRY_EXPECTED", "false")
        }
    }
    buildFeatures { buildConfig = true }
    sourceSets.getByName("main").assets.directories.add(
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
    val upstream = providers.gradleProperty("upstreamAars").getOrElse("../../device-evidence/phase2/upstream-aars")
    "upstreamImplementation"(files("$upstream/tasks_core.aar"))
    "upstreamImplementation"("com.google.mediapipe:tasks-vision:0.10.32") {
        exclude(group = "com.google.mediapipe", module = "tasks-core")
    }
    "upstreamImplementation"("com.google.guava:guava:27.0.1-android")
    "upstreamImplementation"("com.google.flogger:flogger:0.6")
    "upstreamImplementation"("com.google.flogger:flogger-system-backend:0.6")
    "upstreamImplementation"("com.google.protobuf:protobuf-javalite:4.28.3")
    "upstreamImplementation"("androidx.annotation:annotation:1.1.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}

tasks.register("exportAuditGraph") {
    doLast {
        for (variant in listOf("included", "excluded", "upstream")) {
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
