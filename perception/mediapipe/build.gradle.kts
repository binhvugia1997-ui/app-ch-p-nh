import java.security.MessageDigest

plugins { alias(libs.plugins.android.library) }
android {
    namespace = "com.aiphotographer.perception.mediapipe"
    compileSdk = 37
    defaultConfig {
        minSdk = 24; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint { warningsAsErrors = true }
    androidResources { noCompress += "task" }
}
dependencies {
    api(project(":perception:api"))
    implementation(libs.mediapipe.vision) {
        exclude(group = "com.google.mediapipe", module = "tasks-core")
    }
    implementation(libs.mediapipe.core.upstream)
    implementation(libs.guava.android)
    implementation(libs.flogger)
    implementation(libs.flogger.system.backend)
    implementation(libs.protobuf.javalite)
    implementation(libs.coroutines.android)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.junit)
}

val verifyLocalArtifacts = tasks.register("verifyLocalArtifacts") {
    val expected = mapOf(
        "third_party/maven/com/aiphotographer/thirdparty/mediapipe-tasks-core/0.10.32-upstream/mediapipe-tasks-core-0.10.32-upstream.aar" to "f05d8c4432613342fa15d93914d0d7079381b941f4e1cd069dee9f41aa5c365f",
        "perception/mediapipe/src/main/assets/models/pose_landmarker_lite.task" to "59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a",
        "perception/mediapipe/src/main/assets/models/pose_landmarker_full.task" to "5134a3aad27a58b93da0088d431f366da362b44e3ccfbe3462b3827a839011b1",
        "perception/mediapipe/src/main/assets/models/face_landmarker.task" to "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",
    )
    inputs.files(expected.keys.map(rootProject::file))
    doLast {
        expected.forEach { (path, hash) ->
            val actual = MessageDigest.getInstance("SHA-256").digest(rootProject.file(path).readBytes())
                .joinToString("") { "%02x".format(it) }
            check(actual == hash) { "Unreviewed perception artifact: $path" }
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyLocalArtifacts) }
