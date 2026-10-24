plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.soundflow.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
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
    // Lecture audio/vidéo — en "api" car le module app importe directement des types
    // Media3 (ex: @UnstableApi) pour ses écrans et son ViewModel du lecteur.
    api("androidx.media3:media3-exoplayer:1.4.0")
    api("androidx.media3:media3-session:1.4.0")
    api("androidx.media3:media3-ui:1.4.0")
    api("androidx.media3:media3-exoplayer-hls:1.4.0")
    api("androidx.media3:media3-transformer:1.4.0")
    api("com.google.guava:guava:33.2.1-android")

    // Extraction YouTube sans API officielle
    // v0.26.2 (dernière stable) inclut le correctif officiel "Fix page reload required
    // error on streams" (v0.25.1) qui corrige exactement le bug qu'on a rencontré,
    // ainsi qu'un fix pour ne plus utiliser le client WEB pour les URLs de flux (v0.24.6).
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Base de données locale
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Téléchargements en arrière-plan
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Réseau (LRCLIB, requêtes légères)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Sérialisation (pour la persistance locale des données utilisateur)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
