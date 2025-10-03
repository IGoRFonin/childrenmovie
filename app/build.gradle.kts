plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.childrenmovie"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.childrenmovie"
        minSdk = 24
        targetSdk = 36
        versionCode = project.property("VERSION_CODE").toString().toInt()
        versionName = project.property("VERSION_NAME").toString()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    // --- БАЗОВЫЕ ЗАВИСИМОСТИ ANDROID ---
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // --- UI: JETPACK COMPOSE ---
    // Bill of Materials (BOM) - управляет версиями всех Compose-библиотек
    val composeBom = platform("androidx.compose:compose-bom:2024.02.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Основные компоненты Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // --- Архитектура: ViewModel и Навигация ---
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // --- Сеть и Парсинг ---
    // OkHttp - для выполнения сетевых запросов
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Moshi - для парсинга JSON в Kotlin-объекты
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    // Jsoup - для парсинга HTML-страниц
    implementation("org.jsoup:jsoup:1.17.2")

    // --- Изображения: Coil ---
    // Библиотека для загрузки изображений из сети
    implementation("io.coil-kt:coil-compose:2.6.0")

    // --- Видео: ExoPlayer ---
    implementation("androidx.media3:media3-exoplayer:1.3.0")
    implementation("androidx.media3:media3-ui:1.3.0")

    // --- Асинхронность: Kotlin Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // --- ТЕСТОВЫЕ ЗАВИСИМОСТИ ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}