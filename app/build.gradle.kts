plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

android {
    namespace = "dev.nayan.hermes"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    ndkVersion = "28.2.13676358"
    defaultConfig {
        applicationId = "dev.nayan.hermes.port"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-phase1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    packaging { jniLibs { useLegacyPackaging = true } }
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/assets"))
}

chaquopy {
    defaultConfig {
        version = "3.14"
        buildPython(System.getenv("HERMES_BUILD_PYTHON") ?: "python3.14")
        pip {
            options("--only-binary", ":all:")
            options("--find-links", rootProject.file("wheelhouse").absolutePath)
            install("-r", rootProject.file("requirements-android.txt").absolutePath)
        }
        pyc { src = false }
    }
}

val stageHermes by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine(System.getenv("HERMES_BUILD_PYTHON") ?: "python3.14", "scripts/stage_upstream.py")
    inputs.file(rootProject.file("dependencies.json"))
    inputs.dir(rootProject.file("patches"))
    inputs.file(rootProject.file("scripts/stage_upstream.py"))
    outputs.dir(layout.buildDirectory.dir("generated"))
}
tasks.configureEach {
    if (name == "preBuild" || name.endsWith("Assets")) dependsOn(stageHermes)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
