plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.soundflow.mobile"
    //noinspection GradleDependency
    compileSdk = 35

    defaultConfig {
        applicationId = "com.soundflow.mobile"
        minSdk = 26 // Android 8.0+, couvre la grande majorité des appareils actifs
        //noinspection GradleDependency,ExpiredTargetSdkVersion,EditedTargetSdkVersion
        targetSdk = 34
        versionCode = 8
        versionName = "1.0.7"

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            pickFirsts += listOf("**/libpython*.so", "**/libffmpeg*.so")
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core"))

    // Le moteur principal (yt-dlp version Android)
    //noinspection GradleDependency,UnsatisfiedDynamicLibrary
    implementation("com.github.yausername.youtubedl-android:library:0.14.0")

    // L'extension FFmpeg pour l'extraction et la conversion audio
    //noinspection GradleDependency,UnsatisfiedDynamicLibrary
    implementation("com.github.yausername.youtubedl-android:ffmpeg:0.14.0")

    // Thème XML de démarrage (avant que Compose ne prenne le relais)
    //noinspection GradleDependency
    implementation("com.google.android.material:material:1.12.0")

    // Compose
    //noinspection GradleDependency
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.7.0")
    //noinspection GradleDependency
    implementation("androidx.activity:activity-compose:1.10.1")
    //noinspection GradleDependency
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Persistance locale (playlists, historique, téléchargements)
    //noinspection GradleDependency
    implementation("androidx.datastore:datastore-preferences:1.1.3")
    //noinspection GradleDependency,DifferentKotlinGradleVersion,DifferentStdlibGradleVersion
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    //noinspection GradleDependency
    implementation("androidx.navigation:navigation-compose:2.8.5")
    //noinspection GradleDependency
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    //noinspection GradleDependency
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    //noinspection GradleDependency
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    debugImplementation("androidx.compose.ui:ui-tooling")
    // Gestion des tâches en arrière-plan (WorkManager)
    //noinspection GradleDependency
    implementation("androidx.work:work-runtime-ktx:2.10.0")

}
