### Детализация: Этап 1. Настройка проекта и зависимостей

Этот этап — фундамент твоего приложения. Мы добавим все необходимые "инструменты" (библиотеки) и настроим проект для дальнейшей работы.

#### **Задача 1.1: Добавление зависимостей**

1.  **Открой файл `app/build.gradle.kts`**.
    В Android Studio в дереве проекта слева найди и открой этот файл. Это основной конфигурационный файл твоего приложения.

2.  **Скопируй и вставь этот код** в самый конец файла, после существующего блока `dependencies { ... }`. Этот блок определяет набор библиотек, которые мы будем использовать.

    ```kotlin
    // Поместите этот код в конец файла app/build.gradle.kts

    dependencies {

        // --- БАЗОВЫЕ ЗАВИСИМОСТИ ANDROID (уже должны быть в файле) ---
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

        // --- Асинхронность: Kotlin Coroutines (обычно уже есть) ---
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

        // --- ТЕСТОВЫЕ ЗАВИСИМОСТИ (уже должны быть в файле) ---
        testImplementation("junit:junit:4.13.2")
        androidTestImplementation("androidx.test.ext:junit:1.1.5")
        androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
        androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    }
    ```
    *   **Важно:** Твой существующий блок `dependencies` может содержать похожие строки. Просто замени его содержимое целиком на код выше, чтобы избежать дубликатов.

3.  **Синхронизируй проект.**
    После внесения изменений вверху файла появится желтая плашка с надписью "This file has changed...". Нажми на кнопку **`Sync Now`**. Android Studio скачает все указанные библиотеки и подключит их к проекту. Это может занять от нескольких секунд до минуты.

#### **Задача 1.2: Настройка разрешений**

1.  **Открой файл `app/src/main/AndroidManifest.xml`**.
    Этот файл — "паспорт" твоего приложения для системы Android.

2.  **Добавь разрешение на использование интернета.**
    Найди в файле тег `<manifest ...>`. Сразу после него, но **перед** тегом `<application ...>`, вставь следующую строку:

    ```xml
    <!-- Разрешение на доступ в интернет -->
    <uses-permission android:name="android.permission.INTERNET" />
    ```

    Файл должен выглядеть примерно так:

    ```xml
    <?xml version="1.0" encoding="utf-8"?>
    <manifest xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:tools="http://schemas.android.com/tools">

        <!-- ВОТ СЮДА ВСТАВЛЯЕМ РАЗРЕШЕНИЕ -->
        <uses-permission android:name="android.permission.INTERNET" />

        <application
            android:allowBackup="true"
            android:icon="@mipmap/ic_launcher"
            ... >
            ...
        </application>

    </manifest>
    ```

#### **Задача 1.3: Создание структуры пакетов**

Чтобы наш код был хорошо организован, создадим папки (в терминах Java/Kotlin — пакеты) для каждого архитектурного слоя.

1.  **Переключись на вид "Project"**.
    В левой панели Android Studio, где отображаются файлы, в выпадающем меню выбери `Project`. Это покажет реальную структуру папок, что удобнее для этой задачи.

2.  **Найди свой основной пакет.**
    Пройди по пути `app/src/main/java/`. Внутри будет папка с названием твоего пакета, например, `com/example/myapplication`.

3.  **Создай новые пакеты.**
    Нажми правой кнопкой мыши на свою папку (например, `myapplication`) и выбери `New` -> `Package`.

    *   В появившемся окне введи `data` и нажми Enter.
    *   Снова нажми правой кнопкой на `myapplication`, выбери `New` -> `Package`, введи `ui` и нажми Enter.
    *   Повтори то же самое для пакета `model`.

В итоге у тебя должна получиться следующая структура:

```
└── app
    └── src
        └── main
            └── java
                └── com
                    └── yourcompany
                        └── kidsplayer  <-- Твой основной пакет
                            ├── data    <-- Созданная папка
                            ├── model   <-- Созданная папка
                            ├── ui      <-- Созданная папка
                            └── MainActivity.kt (файл уже существует)
```

**Опционально (можно сделать сейчас или на Этапе 5):**
Для удобства можно сразу создать файл `Constants.kt` в пакете `model`, куда мы будем складывать все константы приложения (URL по умолчанию, PIN-код и т.д.). Подробные инструкции будут в Задаче 5.2 на Этапе 5.
