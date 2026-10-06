plugins { alias(libs.plugins.android.library) }
android {
    namespace = "com.aiphotographer.camera"
    compileSdk = 37
    defaultConfig { minSdk = 24; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint { warningsAsErrors = true }
}
dependencies {
    api(project(":core:model"))
    api(project(":core:geometry"))
    api(project(":perception:api"))
    api(libs.camera.view)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
}
