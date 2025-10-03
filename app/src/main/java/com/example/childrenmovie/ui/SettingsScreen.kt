// Файл: ui/SettingsScreen.kt
package com.example.childrenmovie.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.childrenmovie.model.AppUpdateInfo
import java.io.File

@Composable
fun SettingsScreen(
    currentUrl: String,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    updateState: UpdateState,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: (String) -> Unit,
    onInstallUpdate: (File) -> Unit
) {
    // Локальное состояние для текста в поле ввода
    var urlText by remember { mutableStateOf(currentUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        // Секция URL контента
        OutlinedTextField(
            value = urlText,
            onValueChange = { urlText = it },
            label = { Text("URL файла с контентом") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { onSave(urlText) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сохранить и обновить")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { onReset() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сбросить к умолчанию")
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // Секция обновления приложения
        Text("Обновление приложения", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        when (updateState) {
            is UpdateState.Idle -> {
                Button(
                    onClick = onCheckUpdate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Проверить обновление")
                }
            }

            is UpdateState.Checking -> {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text("Проверка обновления...")
            }

            is UpdateState.UpdateAvailable -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Доступно обновление!", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Текущая версия: ${updateState.updateInfo.currentVersion}")
                        Text("Новая версия: ${updateState.updateInfo.availableVersion}")
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { onDownloadUpdate(updateState.updateInfo.downloadUrl) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Скачать и установить")
                        }
                    }
                }
            }

            is UpdateState.NoUpdateAvailable -> {
                Text("У вас установлена последняя версия", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onCheckUpdate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Проверить снова")
                }
            }

            is UpdateState.Downloading -> {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text("Загрузка обновления...")
            }

            is UpdateState.DownloadComplete -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Загрузка завершена!", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { onInstallUpdate(updateState.file) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Установить")
                        }
                    }
                }
            }

            is UpdateState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Ошибка", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(updateState.message)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = onCheckUpdate,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Попробовать снова")
                        }
                    }
                }
            }
        }
    }
}