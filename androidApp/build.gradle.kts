plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "edu.moravian.markdowneditor.android"
    compileSdk = 34
    defaultConfig {
        applicationId = "edu.moravian.markdowneditor.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.compose.ui:ui:1.5.1")
    implementation("androidx.compose.foundation:foundation:1.5.1")
    implementation("androidx.compose.material3:material3:1.2.0-alpha08")
    implementation("androidx.activity:activity-compose:1.7.2")


    implementation("org.jetbrains:markdown:0.5.0")
    implementation("com.mohamedrejeb.ksoup:ksoup-html:0.2.1")
    implementation("com.mohamedrejeb.ksoup:ksoup-entites:0.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("co.touchlab:kermit:2.0.0-RC5")
    api("io.github.qdsfdhvh:image-loader:1.5.2")
}