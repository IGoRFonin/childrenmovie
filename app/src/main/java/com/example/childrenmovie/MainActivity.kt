// Файл: MainActivity.kt
package com.example.childrenmovie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.example.childrenmovie.data.ContentRepository
import com.example.childrenmovie.data.LocalDataSource
import com.example.childrenmovie.data.RemoteDataSource
import com.example.childrenmovie.data.SettingsManager
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import com.example.childrenmovie.ui.GalleryGrid
import com.example.childrenmovie.ui.GalleryUiState
import com.example.childrenmovie.ui.GalleryViewModel
import com.example.childrenmovie.ui.PlayerScreen
import com.example.childrenmovie.ui.PlayerUiState
import com.example.childrenmovie.ui.PlayerViewModel
import com.example.childrenmovie.ui.Screen
import com.example.childrenmovie.ui.SeriesDetailsScreen
import com.example.childrenmovie.ui.SeriesDetailsViewModel
import com.example.childrenmovie.ui.SeriesUiState
import com.example.childrenmovie.ui.SettingsScreen
import com.example.childrenmovie.ui.PinDialog
import com.example.childrenmovie.ui.theme.ChildrenMovieTheme
import okhttp3.OkHttpClient
import android.widget.Toast
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.example.childrenmovie.model.PARENTAL_PIN
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Включаем полноэкранный режим
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Скрываем системные бары (status bar и navigation bar)
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            // Immersive mode: бары появляются при свайпе и снова скрываются
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // --- СОЗДАНИЕ ЗАВИСИМОСТЕЙ (РУЧНОЕ ВНЕДРЕНИЕ) ---
        // В больших проектах для этого используют Hilt/Dagger

        val settingsManager = SettingsManager(applicationContext)
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val remoteDataSource = RemoteDataSource(okHttpClient, moshi)
        val localDataSource = LocalDataSource(applicationContext)
        val repository = ContentRepository(remoteDataSource, localDataSource, moshi, settingsManager)

        setContent {
            ChildrenMovieTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = Screen.Gallery.route) {

                        // Маршрут для главного экрана (галереи)
                        composable(Screen.Gallery.route) {
                            // Создаем ViewModel с помощью фабрики, чтобы передать репозиторий
                            val viewModel: GalleryViewModel = viewModel(
                                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                        @Suppress("UNCHECKED_CAST")
                                        return GalleryViewModel(repository) as T
                                    }
                                }
                            )
                            val uiState by viewModel.uiState.collectAsState()

                            var showPinDialog by remember { mutableStateOf(false) }

                            if (showPinDialog) {
                                PinDialog(
                                    onDismiss = { showPinDialog = false },
                                    onConfirm = { pin ->
                                        showPinDialog = false
                                        if (pin == PARENTAL_PIN) {
                                            navController.navigate(Screen.Settings.route)
                                        } else {
                                            Toast.makeText(applicationContext, "Неверный PIN", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }

                            Scaffold(
                                topBar = {
                                    TopAppBar(
                                        title = {
                                            Text(
                                                "Детский Кинозал",
                                                modifier = Modifier.pointerInput(Unit) {
                                                    detectTapGestures(
                                                        onLongPress = {
                                                            showPinDialog = true
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    )
                                }
                            ) { paddingValues ->
                                when (val state = uiState) {
                                    is GalleryUiState.Loading -> {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(paddingValues)
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                    is GalleryUiState.Success -> {
                                        GalleryGrid(
                                            contentList = state.content,
                                            onContentClick = { item ->
                                                // Логика навигации
                                                if (item.type == "series") {
                                                    navController.navigate(Screen.SeriesDetails.createRoute(item.id))
                                                } else {
                                                    // Навигация на плеер для фильмов
                                                    item.pageUrl?.let { url ->
                                                        navController.navigate(Screen.Player.createRoute(url))
                                                    }
                                                }
                                            },
                                            modifier = Modifier.padding(paddingValues)
                                        )
                                    }
                                    is GalleryUiState.Error -> {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(paddingValues)
                                        ) {
                                            Text("Ошибка: ${state.message}")
                                        }
                                    }
                                }
                            }
                        }

                        // Маршрут для экрана с эпизодами (теперь не пустой)
                        composable(
                            route = Screen.SeriesDetails.route,
                            arguments = listOf(navArgument("seriesId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            // Получаем seriesId из аргументов навигации
                            val seriesId = backStackEntry.arguments?.getString("seriesId") ?: ""

                            val viewModel: SeriesDetailsViewModel = viewModel(
                                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                        @Suppress("UNCHECKED_CAST")
                                        return SeriesDetailsViewModel(seriesId, repository) as T
                                    }
                                }
                            )
                            val uiState by viewModel.uiState.collectAsState()

                            when (val state = uiState) {
                                is SeriesUiState.Loading -> {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        CircularProgressIndicator()
                                    }
                                }
                                is SeriesUiState.Success -> {
                                    SeriesDetailsScreen(
                                        series = state.series,
                                        onEpisodeClick = { episodePageUrl ->
                                            // Навигация на плеер для эпизодов
                                            navController.navigate(Screen.Player.createRoute(episodePageUrl))
                                        }
                                    )
                                }
                                is SeriesUiState.Error -> {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text("Ошибка: ${state.message}")
                                    }
                                }
                            }
                        }

                        // Маршрут для плеера
                        composable(
                            route = Screen.Player.route,
                            arguments = listOf(navArgument("encodedUrl") { type = NavType.StringType })
                        ) { backStackEntry ->
                            // Получаем encodedUrl из аргументов навигации
                            val encodedUrl = backStackEntry.arguments?.getString("encodedUrl") ?: ""

                            val viewModel: PlayerViewModel = viewModel(
                                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                        @Suppress("UNCHECKED_CAST")
                                        return PlayerViewModel(encodedUrl, repository) as T
                                    }
                                }
                            )
                            val uiState by viewModel.uiState.collectAsState()

                            when (val state = uiState) {
                                is PlayerUiState.Loading -> {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize().background(Color.Black)
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                                is PlayerUiState.Success -> {
                                    PlayerScreen(
                                        videoUrl = state.videoUrl,
                                        pageUrl = state.pageUrl,
                                        onBackPressed = {
                                            // Инициируем контролируемое закрытие
                                            viewModel.initiateClosing()
                                        },
                                        onPlayerClosed = {
                                            // Плеер сообщает, что ресурсы освобождены
                                            viewModel.onPlayerFullyClosed()
                                            // Навигация происходит только ПОСЛЕ полной очистки
                                            navController.popBackStack()
                                        }
                                    )
                                }
                                is PlayerUiState.Closing -> {
                                    // Показываем черный экран во время закрытия
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black)
                                    )
                                    // PlayerScreen будет удален из композиции → запустится onDispose
                                }
                                is PlayerUiState.Error -> {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text("Ошибка загрузки видео: ${state.message}")
                                    }
                                }
                            }
                        }

                        // Маршрут для настроек
                        composable(Screen.Settings.route) {
                            val coroutineScope = rememberCoroutineScope()
                            SettingsScreen(
                                currentUrl = settingsManager.getContentUrl(),
                                onSave = { newUrl ->
                                    settingsManager.saveContentUrl(newUrl)
                                    Toast.makeText(applicationContext, "Настройки сохранены", Toast.LENGTH_SHORT).show()
                                    navController.popBackStack()
                                },
                                onReset = {
                                    coroutineScope.launch {
                                        settingsManager.resetToDefault()
                                        repository.clearCache()
                                        Toast.makeText(applicationContext, "Сброшено к умолчанию", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}