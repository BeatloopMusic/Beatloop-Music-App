package com.beatloop.music.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.beatloop.music.data.preferences.AudioQuality
import com.beatloop.music.data.preferences.ThemeMode
import com.beatloop.music.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var showDeleteDataDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshIdentityState()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Appearance Section
            item {
                SettingsSection(title = "Appearance")
            }

            // Account & Sync Section
            item {
                SettingsSection(title = "Account & Sync")
            }

            item {
                ClickableSettingItem(
                    title = "Current Identity",
                    subtitle = "${uiState.identityModeLabel}: ${uiState.currentUserId.take(18)}",
                    onClick = { viewModel.refreshIdentityState() },
                    enabled = !uiState.isIdentityActionInProgress
                )
            }

            item {
                ClickableSettingItem(
                    title = "Sign in with Google",
                    subtitle = "Primary account sync across devices",
                    onClick = {
                        activity?.let { viewModel.loginWithGoogle(it) }
                    },
                    enabled = !uiState.isIdentityActionInProgress && activity != null
                )
            }

            if (!uiState.cloudSyncAvailable) {
                item {
                    ClickableSettingItem(
                        title = "Use Guest Cloud Sync",
                        subtitle = "Anonymous fallback when Google sign-in is not used",
                        onClick = { viewModel.loginAnonymously() },
                        enabled = !uiState.isIdentityActionInProgress
                    )
                }
            }

            item {
                ClickableSettingItem(
                    title = "Sync Now",
                    subtitle = "Merge latest local and cloud data",
                    onClick = { viewModel.syncNow() },
                    enabled = !uiState.isIdentityActionInProgress
                )
            }

            if (uiState.isIdentityActionInProgress) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            uiState.statusMessage?.let { status ->
                item {
                    ClickableSettingItem(
                        title = "Sync Status",
                        subtitle = status,
                        onClick = { viewModel.clearStatusMessage() },
                        enabled = true
                    )
                }
            }
            
            item {
                ThemeSettingItem(
                    currentTheme = uiState.themeMode,
                    onThemeChange = { viewModel.setThemeMode(it) }
                )
            }
            
            item {
                SwitchSettingItem(
                    title = "Pure Black Mode",
                    subtitle = "Use pure black background in dark mode",
                    checked = uiState.pureBlackEnabled,
                    onCheckedChange = { viewModel.setPureBlackEnabled(it) }
                )
            }
            
            item {
                SwitchSettingItem(
                    title = "Dynamic Colors",
                    subtitle = "Use colors from album artwork",
                    checked = uiState.dynamicColorEnabled,
                    onCheckedChange = { viewModel.setDynamicColorEnabled(it) }
                )
            }
            
            // Audio Section
            item {
                SettingsSection(title = "Audio")
            }
            
            item {
                AudioQualitySettingItem(
                    currentQuality = uiState.audioQuality,
                    onQualityChange = { viewModel.setAudioQuality(it) }
                )
            }
            
            item {
                SwitchSettingItem(
                    title = "Normalize Volume",
                    subtitle = "Adjust playback volume to the same level",
                    checked = uiState.normalizeAudioEnabled,
                    onCheckedChange = { viewModel.setNormalizeAudioEnabled(it) }
                )
            }
            
            item {
                SwitchSettingItem(
                    title = "Skip Silence",
                    subtitle = "Automatically skip silent parts in songs",
                    checked = uiState.skipSilenceEnabled,
                    onCheckedChange = { viewModel.setSkipSilenceEnabled(it) }
                )
            }
            
            // Playback Section
            item {
                SettingsSection(title = "Playback")
            }
            
            item {
                SwitchSettingItem(
                    title = "Persistent Queue",
                    subtitle = "Save queue when closing the app",
                    checked = uiState.persistentQueueEnabled,
                    onCheckedChange = { viewModel.setPersistentQueueEnabled(it) }
                )
            }
            
            // SponsorBlock Section
            item {
                SettingsSection(title = "SponsorBlock")
            }
            
            item {
                SwitchSettingItem(
                    title = "Enable SponsorBlock",
                    subtitle = "Automatically skip sponsor segments",
                    checked = uiState.sponsorBlockEnabled,
                    onCheckedChange = { viewModel.setSponsorBlockEnabled(it) }
                )
            }
            
            if (uiState.sponsorBlockEnabled) {
                item {
                    SwitchSettingItem(
                        title = "Skip Intros",
                        subtitle = "Skip introduction segments",
                        checked = uiState.skipIntroEnabled,
                        onCheckedChange = { viewModel.setSkipIntroEnabled(it) }
                    )
                }
                
                item {
                    SwitchSettingItem(
                        title = "Skip Outros",
                        subtitle = "Skip ending segments",
                        checked = uiState.skipOutroEnabled,
                        onCheckedChange = { viewModel.setSkipOutroEnabled(it) }
                    )
                }
                
                item {
                    SwitchSettingItem(
                        title = "Skip Self-Promo",
                        subtitle = "Skip self-promotion segments",
                        checked = uiState.skipSelfPromoEnabled,
                        onCheckedChange = { viewModel.setSkipSelfPromoEnabled(it) }
                    )
                }
                
                item {
                    SwitchSettingItem(
                        title = "Skip Music Off-Topic",
                        subtitle = "Skip non-music content",
                        checked = uiState.skipMusicOffTopicEnabled,
                        onCheckedChange = { viewModel.setSkipMusicOffTopicEnabled(it) }
                    )
                }
            }
            
            // Downloads Section
            item {
                SettingsSection(title = "Downloads")
            }
            
            item {
                AudioQualitySettingItem(
                    title = "Download Quality",
                    currentQuality = uiState.downloadQuality,
                    onQualityChange = { viewModel.setDownloadQuality(it) }
                )
            }

            item {
                VideoQualitySettingItem(
                    currentQuality = uiState.videoPlaybackQuality,
                    onQualityChange = { viewModel.setVideoPlaybackQuality(it) }
                )
            }
            
            // Cache Section
            item {
                SettingsSection(title = "Cache")
            }
            
            item {
                CacheSizeSettingItem(
                    currentSizeMb = uiState.maxCacheSizeMb,
                    onSizeChange = { viewModel.setMaxCacheSize(it) }
                )
            }
            
            item {
                ClickableSettingItem(
                    title = "Clear Cache",
                    subtitle = "Free up ${uiState.currentCacheSizeMb} MB",
                    onClick = { viewModel.clearCache() }
                )
            }
            
            // About Section
            item {
                SettingsSection(title = "About")
            }
            
            item {
                ClickableSettingItem(
                    title = "Version",
                    subtitle = "1.0.0",
                    onClick = { }
                )
            }

            // Privacy Section
            item {
                SettingsSection(title = "Privacy")
            }

            item {
                ClickableSettingItem(
                    title = "Delete My Data",
                    subtitle = "Delete cloud records, clear Room data, and reset identity",
                    onClick = { showDeleteDataDialog = true },
                    enabled = !uiState.isIdentityActionInProgress
                )
            }
        }
    }

    if (showDeleteDataDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDataDialog = false },
            title = { Text("Delete My Data") },
            text = {
                Text("This permanently removes your synced Firestore data, clears local Room tables, and resets your user identity.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDataDialog = false
                        viewModel.deleteMyData()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SwitchSettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}

@Composable
private fun ClickableSettingItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = Modifier.clickable(enabled = enabled) { onClick() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSettingItem(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    ListItem(
        headlineContent = { Text("Theme") },
        supportingContent = { Text(currentTheme.name.lowercase().replaceFirstChar { it.uppercase() }) },
        trailingContent = {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.menuAnchor()
                ) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select theme")
                }
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.widthIn(min = 220.dp)
                ) {
                    ThemeMode.entries.forEach { theme ->
                        DropdownMenuItem(
                            text = { Text(theme.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            onClick = {
                                onThemeChange(theme)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioQualitySettingItem(
    title: String = "Streaming Quality",
    currentQuality: AudioQuality,
    onQualityChange: (AudioQuality) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(currentQuality.displayName) },
        trailingContent = {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.menuAnchor()
                ) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select quality")
                }
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.widthIn(min = 220.dp)
                ) {
                    AudioQuality.entries.forEach { quality ->
                        DropdownMenuItem(
                            text = { Text(quality.displayName) },
                            onClick = {
                                onQualityChange(quality)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    )
}

private val AudioQuality.displayName: String
    get() = when (this) {
        AudioQuality.LOW -> "Low (64 kbps)"
        AudioQuality.MEDIUM -> "Medium (128 kbps)"
        AudioQuality.HIGH -> "High (256 kbps)"
        AudioQuality.VERY_HIGH -> "Very High (320 kbps)"
        AudioQuality.BEST -> "Best (320 kbps)"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CacheSizeSettingItem(
    currentSizeMb: Int,
    onSizeChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val sizes = listOf(256, 512, 1024, 2048, 4096)
    
    ListItem(
        headlineContent = { Text("Max Cache Size") },
        supportingContent = { 
            Text("${if (currentSizeMb >= 1024) "${currentSizeMb / 1024} GB" else "$currentSizeMb MB"}") 
        },
        trailingContent = {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.menuAnchor()
                ) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select size")
                }
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.widthIn(min = 220.dp)
                ) {
                    sizes.forEach { size ->
                        DropdownMenuItem(
                            text = { 
                                Text(if (size >= 1024) "${size / 1024} GB" else "$size MB") 
                            },
                            onClick = {
                                onSizeChange(size)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoQualitySettingItem(
    currentQuality: Int,
    onQualityChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val qualities = listOf(144, 240, 360, 480, 720)

    ListItem(
        headlineContent = { Text("Video Playing Quality") },
        supportingContent = { Text("${currentQuality}p") },
        trailingContent = {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.menuAnchor()
                ) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select video quality")
                }
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.widthIn(min = 220.dp)
                ) {
                    qualities.forEach { quality ->
                        DropdownMenuItem(
                            text = { Text("${quality}p") },
                            onClick = {
                                onQualityChange(quality)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
