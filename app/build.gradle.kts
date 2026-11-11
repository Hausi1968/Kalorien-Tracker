import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Eigener Signierschlüssel (release.jks + keystore.properties im Projektordner).
// Wichtig: immer derselbe Schlüssel, sonst lassen sich Updates nicht über die alte Version installieren.
val signierDatei = rootProject.file("keystore.properties")
val signier = Properties().apply { if (signierDatei.exists()) signierDatei.inputStream().use { load(it) } }

android {
    namespace = "de.kh.kalorientracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.kh.kalorientracker"
        minSdk = 28
        targetSdk = 35
        versionCode = 12
        versionName = "0.11.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("de")
    }

    signingConfigs {
        if (signierDatei.exists()) {
            create("eigen") {
                storeFile = rootProject.file(signier.getProperty("storeFile"))
                storePassword = signier.getProperty("storePassword")
                keyAlias = signier.getProperty("keyAlias")
                keyPassword = signier.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Ohne eigenen Schlüssel nur zum Ausprobieren (Debug-Schlüssel); veröffentlicht wird so nie (siehe unten).
            signingConfig = signingConfigs.findByName("eigen") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += "gzip" }

    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

// Eine Release-App mit fremdem Schlüssel ließe sich nicht als Update installieren: Bau dann abbrechen.
gradle.taskGraph.whenReady {
    val baut = allTasks.any { it.project == project && it.name.startsWith("package") && it.name.endsWith("Release") }
    if (baut && !signierDatei.exists()) {
        throw GradleException("keystore.properties fehlt: Release-App wird nicht mit dem Debug-Schlüssel gebaut.")
    }
}

ksp {
    arg("room.generateKotlin", "true")
    // Datenbank-Schema je Version festhalten, damit Updates die Daten sicher übernehmen (Migrationen)
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.code.scanner)
    implementation(libs.health.connect)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
