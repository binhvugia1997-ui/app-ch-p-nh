import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins { alias(libs.plugins.android.application); alias(libs.plugins.compose) }

val noticesDirectory = layout.buildDirectory.dir("generated/notices")
tasks.register("exportRuntimeAudit") {
    val artifacts = configurations.named("releaseRuntimeClasspath").get().incoming.artifactView {
        componentFilter { it !is ProjectComponentIdentifier }
    }.artifacts
    doLast {
        val file = rootProject.layout.buildDirectory.file("audit/runtime-artifacts.tsv").get().asFile
        file.parentFile.mkdirs()
        file.writeText(artifacts.artifacts.sortedBy { it.id.componentIdentifier.displayName }.joinToString("\n") {
            "${it.id.componentIdentifier.displayName}\t${it.file.absolutePath}"
        })
        file.resolveSibling("runtime-graph.txt").writeText(configurations.getByName("releaseRuntimeClasspath").incoming.resolutionResult.allDependencies
            .map { "${it.from.id.displayName} -> ${it.requested.displayName}" }.sorted().joinToString("\n"))
    }
}
val generateThirdPartyNotices = tasks.register("generateThirdPartyNotices") {
    val runtime = configurations.named("releaseRuntimeClasspath")
    val runtimeArtifacts = runtime.get().incoming.artifactView {
        componentFilter { it !is ProjectComponentIdentifier }
    }.artifacts
    inputs.files(runtimeArtifacts.artifactFiles)
    inputs.dir(rootProject.file("third_party/notices"))
    inputs.file(rootProject.file("third_party/runtime-components.tsv"))
    outputs.dir(noticesDirectory)
    doLast {
        val artifacts = runtimeArtifacts.artifacts.sortedBy { it.id.componentIdentifier.displayName }
        check(artifacts.none { it.id.componentIdentifier.displayName.contains("datatransport") ||
            it.id.componentIdentifier.displayName.contains("com.google.firebase") ||
            it.id.componentIdentifier.displayName.startsWith("com.google.mediapipe:tasks-core:") }) {
            "Unapproved telemetry-capable MediaPipe runtime dependency."
        }
        val vision = artifacts.single { it.id.componentIdentifier.displayName == "com.google.mediapipe:tasks-vision:0.10.32" }
        check(MessageDigest.getInstance("SHA-256").digest(vision.file.readBytes()).joinToString("") { "%02x".format(it) } ==
            "d6e69475707d07a24478e9ff00c437c48c9a834ffb04d4e6e0ba907defa87777") { "Unreviewed MediaPipe Vision artifact" }
        val registry = rootProject.file("third_party/runtime-components.tsv").readLines().filter { it.isNotBlank() }
            .associate { val parts = it.split('\t'); parts[0] to parts[1] }
        check(artifacts.map { it.id.componentIdentifier.displayName }.toSet() == registry.keys) {
            "Runtime dependencies differ from third_party/runtime-components.tsv; audit the change first."
        }
        val text = StringBuilder("Third-party components only. This does not license AI Photographer.\n\n")
        artifacts.forEach { artifact ->
            val id = artifact.id.componentIdentifier.displayName
            text.appendLine("$id — ${registry.getValue(id)} (see bundled text; upstream notices below)")
        }
        text.appendLine()
        rootProject.file("third_party/notices").listFiles()!!.sortedBy { it.name }.forEach {
            text.appendLine("--- ${it.name} ---").appendLine(it.readText())
        }
        val seen = mutableSetOf<String>()
        fun retain(name: String, bytes: ByteArray) {
            if (name.contains("LICENSE", true) || name.contains("NOTICE", true)) {
                val content = bytes.toString(Charsets.UTF_8)
                if (seen.add(content)) text.appendLine("--- upstream $name ---").appendLine(content)
            }
        }
        artifacts.filter { it.file.extension in setOf("jar", "aar") }.forEach { artifact ->
            ZipFile(artifact.file).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (!entry.isDirectory && (entry.name.contains("LICENSE", true) || entry.name.contains("NOTICE", true))) {
                        retain("${artifact.id.componentIdentifier.displayName}/${entry.name}", zip.getInputStream(entry).use { it.readBytes() })
                    }
                    if (entry.name == "classes.jar") {
                        ZipInputStream(zip.getInputStream(entry)).use { nested ->
                            var inner = nested.nextEntry
                            while (inner != null) {
                                if (!inner.isDirectory && (inner.name.contains("LICENSE", true) || inner.name.contains("NOTICE", true))) {
                                    retain("${artifact.id.componentIdentifier.displayName}/${inner.name}", nested.readBytes())
                                }
                                inner = nested.nextEntry
                            }
                        }
                    }
                }
            }
        }
        noticesDirectory.get().file("third_party_notices.txt").asFile.apply { parentFile.mkdirs(); writeText(text.toString()) }
    }
}
android {
    namespace = "com.aiphotographer.app"
    compileSdk = 37
    testBuildType = providers.gradleProperty("instrumentBuildType").getOrElse("debug")
    defaultConfig {
        applicationId = "com.aiphotographer.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        create("profile") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            testProguardFiles("test-proguard-rules.pro")
            proguardFiles("profile-test-target-rules.pro")
        }
    }
    sourceSets.getByName("main").assets.directories.add(noticesDirectory.get().asFile.path)
    buildFeatures { compose = true; buildConfig = true }
    bundle { language { enableSplit = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint { warningsAsErrors = true }
}
tasks.configureEach {
    if ((name.startsWith("merge") && name.endsWith("Assets")) || name.contains("lint", ignoreCase = true)) {
        dependsOn(generateThirdPartyNotices)
    }
    if (name in setOf("connectedDebugAndroidTest", "connectedProfileAndroidTest")) {
        doLast {
            val variant = if (name.contains("Profile")) "profile" else "debug"
            val reports = layout.buildDirectory.dir("outputs/androidTest-results/connected/$variant").get().asFile
                .listFiles()?.filter { it.name.startsWith("TEST-") && it.extension == "xml" }.orEmpty()
            val count = reports.sumOf { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
                .documentElement.getAttribute("tests").toInt() }
            check(count > 0) { "Instrumentation executed zero tests; do not report this as PASS." }
        }
    }
}
dependencies {
    implementation(project(":feature:camera"))
    implementation(project(":perception:mediapipe"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.compose)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.junit)
}
