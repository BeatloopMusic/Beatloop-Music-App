package com.beatloop.music.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.beatloop.music.data.preferences.AudioQuality
import com.beatloop.music.data.preferences.ThemeMode
import com.beatloop.music.domain.recommendation.RecommendationContentRules
import com.beatloop.music.ui.components.PremiumFilterChipRow
import com.beatloop.music.ui.components.PremiumGlassSurface
import com.beatloop.music.ui.components.PremiumScreenBackground
import com.beatloop.music.ui.components.PremiumSectionHeader
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
    val languageOptions = remember { RecommendationContentRules.supportedLanguages }

    var showDeleteDataDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshIdentityState()
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        }
    ) { padding ->
        PremiumScreenBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                item {
                    PremiumGlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "${uiState.identityModeLabel} account",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = uiState.currentUserId.take(24),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = { viewModel.syncNow() },
                                    enabled = !uiState.isIdentityActionInProgress
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sync now")
                                }
                                TextButton(
                                    onClick = { viewModel.backupNow() },
                                    enabled = !uiState.isIdentityActionInProgress
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Backup,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Backup")
                                }
                            }
                        }
                    }
                }

                item {
                    AnimatedVisibility(visible = uiState.statusMessage != null) {
                        PremiumGlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable { viewModel.clearStatusMessage() }
                        ) {
                            Text(
                                text = uiState.statusMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(14.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                item {
                    PremiumSectionHeader(
                        title = "Account & Sync",
                        subtitle = "Identity, sign-in, and cloud synchronization"
                    )
                }
                item {
                    SettingsGroupCard {
                        ActionSettingRow(
                            icon = Icons.AutoMirrored.Filled.Login,
                            title = "Sign in with Google",
                            subtitle = "Primary account sync across devices",
                            enabled = !uiState.isIdentityActionInProgress && activity != null,
                            onClick = {
                                activity?.let { viewModel.loginWithGoogle(it) }
                            }
                        )
                        if (!uiState.cloudSyncAvailable) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                            ActionSettingRow(
                                icon = Icons.Default.CloudSync,
                                title = "Use Guest Cloud Sync",
                                subtitle = "Anonymous fallback when Google sign-in is not used",
                                enabled = !uiState.isIdentityActionInProgress,
                                onClick = { viewModel.loginAnonymously() }
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        ActionSettingRow(
                            icon = Icons.Default.CloudSync,
                            title = "Sync Now",
                            subtitle = "Merge latest local and cloud data",
                            enabled = !uiState.isIdentityActionInProgress,
                            onClick = { viewModel.syncNow() }
                        )
                    }
                }

                item {
                    PremiumSectionHeader(
                        title = "Appearance",
                        subtitle = "Theme, color behavior, and contrast"
                    )
                }
                item {
                    SettingsGroupCard {
                        ThemePreviewSelector(
                            currentTheme = uiState.themeMode,
                            onThemeChange = { viewModel.setThemeMode(it) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        SwitchSettingRow(
                            icon = Icons.Default.NightsStay,
                            title = "Pure Black Mode",
                            subtitle = "Use pure black background in dark mode",
                            checked = uiState.pureBlackEnabled,
                            onCheckedChange = { viewModel.setPureBlackEnabled(it) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        SwitchSettingRow(
                            icon = Icons.Default.Brush,
                            title = "Dynamic Colors",
                            subtitle = "Use accents influenced by album art",
                            checked = uiState.dynamicColorEnabled,
                            onCheckedChange = { viewModel.setDynamicColorEnabled(it) }
                        )
                    }
                }

                item {
                    PremiumSectionHeader(
                        title = "Personalization",
                        subtitle = "Language rules for recommendations and home feed"
                    )
                }
                item {
                    SettingsGroupCard {
                        ContentLanguageRow(
                            currentLanguage = uiState.contentLanguage,
                            availableLanguages = languageOptions,
                            onLanguageSelected = { viewModel.setContentLanguage(it) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        PreferredLanguagesRow(
                            selectedLanguages = uiState.preferredLanguages,
                            availableLanguages = languageOptions,
                            onToggleLanguage = { viewModel.togglePreferredLanguage(it) }
                        )
                    }
                }

                item {
                    PremiumSectionHeader(
                        title = "Audio & Playback",
                        subtitle = "Listening quality, queue behavior, and SponsorBlock"
                    )
                }
                item {
                    SettingsGroupCard {
                        AudioQualityRow(
                            icon = Icons.Default.GraphicEq,
                            title = "Streaming Quality",
                            currentQuality = uiState.audioQuality,
                            onQualityChange = { viewModel.setAudioQuality(it) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        SwitchSettingRow(
                            icon = Icons.Default.Tune,
                            title = "Normalize Volume",
                            subtitle = "Adjust playback volume to the same level",
                            checked = uiState.normalizeAudioEnabled,
                            onCheckedChange = { viewModel.setNormalizeAudioEnabled(it) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        SwitchSettingRow(
                            icon = Icons.Default.Tune,
                            title = "Skip Silence",
                            subtitle = "Automatically skip silent parts in tracks",
                            checked = uiState.skipSilenceEnabled,
                            onCheckedChange = { viewModel.setSkipSilenceEnabled(it) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        SwitchSettingRow(
                            icon = Icons.Default.Tune,
                            title = "Persistent Queue",
                            subtitle = "Restore queue after app restarts",
                            checked = uiState.persistentQueueEnabled,
                            onCheckedChange = { viewModel.setPersistentQueueEnabled(it) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        SwitchSettingRow(
                            icon = Icons.Default.Tune,
                            title = "Enable SponsorBlock",
                            subtitle = "Automatically skip sponsor segments",
                            checked = uiState.sponsorBlockEnabled,
                            onCheckedChange = { viewModel.setSponsorBlockEnabled(it) }
                        )
                        if (uiState.sponsorBlockEnabled) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                            SwitchSettingRow(
                                icon = Icons.Default.Tune,
                                title = "Skip Intro",
                                subtitle = "Skip introduction segments",
                                checked = uiState.skipIntroEnabled,
                                onCheckedChange = { viewModel.setSkipIntroEnabled(it) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                            SwitchSettingRow(
                                icon = Icons.Default.Tune,
                                title = "Skip Outro",
                                subtitle = "Skip ending segments",
                                checked = uiState.skipOutroEnabled,
                                onCheckedChange = { viewModel.setSkipOutroEnabled(it) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                            SwitchSettingRow(
                                icon = Icons.Default.Tune,
                                title = "Skip Self Promo",
                                subtitle = "Skip self-promotion segments",
                                checked = uiState.skipSelfPromoEnabled,
                                onCheckedChange = { viewModel.setSkipSelfPromoEnabled(it) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                            SwitchSettingRow(
                                icon = Icons.Default.Tune,
                                title = "Skip Music Off Topic",
                                subtitle = "Skip non-music sections",
                                checked = uiState.skipMusicOffTopicEnabled,
                                onCheckedChange = { viewModel.setSkipMusicOffTopicEnabled(it) }
                            )
                        }
                    }
                }

                item {
                    PremiumSectionHeader(
                        title = "Downloads & Cache",
                        subtitle = "Offline quality and local storage usage"
                    )
                }
                item {
                    SettingsGroupCard {
                        AudioQualityRow(
                            icon = Icons.Default.Download,
                            title = "Download Quality",
                            currentQuality = uiState.downloadQuality,
                            onQualityChange = { viewModel.setDownloadQuality(it) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        VideoQualityRow(
                            currentQuality = uiState.videoPlaybackQuality,
                            onQualityChange = { viewModel.setVideoPlaybackQuality(it) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        CacheSizeRow(
                            currentSizeMb = uiState.maxCacheSizeMb,
                            onSizeChange = { viewModel.setMaxCacheSize(it) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        ActionSettingRow(
                            icon = Icons.Default.DeleteForever,
                            title = "Clear Cache",
                            subtitle = "Free up ${uiState.currentCacheSizeMb} MB",
                            onClick = { viewModel.clearCache() }
                        )
                    }
                }

                item {
                    PremiumSectionHeader(
                        title = "Privacy & Data",
                        subtitle = "Export, backup, and account data controls"
                    )
                }
                item {
                    SettingsGroupCard {
                        ActionSettingRow(
                            icon = Icons.Default.DataObject,
                            title = "Export Data",
                            subtitle = "Generate a snapshot of synced preferences",
                            enabled = !uiState.isIdentityActionInProgress,
                            onClick = { viewModel.exportDataSnapshot() }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        ActionSettingRow(
                            icon = Icons.Default.Backup,
                            title = "Backup Now",
                            subtitle = "Run immediate backup sync",
                            enabled = !uiState.isIdentityActionInProgress,
                            onClick = { viewModel.backupNow() }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        ActionSettingRow(
                            icon = Icons.Default.DeleteForever,
                            title = "Delete My Data",
                            subtitle = "Delete cloud records, local tables, and reset identity",
                            enabled = !uiState.isIdentityActionInProgress,
                            onClick = { showDeleteDataDialog = true }
                        )
                    }
                }

                item {
                    PremiumSectionHeader(
                        title = "About",
                        subtitle = "Build and product information"
                    )
                }
                item {
                    SettingsGroupCard {
                        ActionSettingRow(
                            icon = Icons.Default.Info,
                            title = "Version",
                            subtitle = "1.0.0",
                            onClick = { }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                        ActionSettingRow(
                            icon = Icons.Default.PrivacyTip,
                            title = "Privacy",
                            subtitle = "Manage data handling and consent",
                            onClick = { }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDataDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDataDialog = false },
            title = { Text("Delete My Data") },
            text = {
                Text("This permanently removes your synced Firestore data, clears local Room tables, and resets your identity.")
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
private fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    PremiumGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun ActionSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(imageVector = icon, contentDescription = null)
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun SwitchSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(imageVector = icon, contentDescription = null)
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

@Composable
private fun ThemePreviewSelector(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    val options = listOf(
        ThemeMode.SYSTEM to "System",
        ThemeMode.LIGHT to "Light",
        ThemeMode.DARK to "Dark"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text("Theme") },
            supportingContent = { Text("Animated preview when switching theme") },
            leadingContent = {
                Icon(imageVector = Icons.Default.Brush, contentDescription = null)
            }
        )

        PremiumFilterChipRow(
            items = options.map { it.second },
            selectedItem = options.firstOrNull { it.first == currentTheme }?.second ?: "System",
            modifier = Modifier.padding(bottom = 12.dp),
            onItemSelected = { selectedLabel ->
                options.firstOrNull { it.second == selectedLabel }?.first?.let(onThemeChange)
            }
        )
    }
}

@Composable
private fun AudioQualityRow(
    icon: ImageVector,
    title: String,
    currentQuality: AudioQuality,
    onQualityChange: (AudioQuality) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(currentQuality.displayName) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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

@Composable
private fun CacheSizeRow(
    currentSizeMb: Int,
    onSizeChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val sizes = listOf(256, 512, 1024, 2048, 4096)

    ListItem(
        headlineContent = { Text("Max Cache Size") },
        supportingContent = {
            Text(if (currentSizeMb >= 1024) "${currentSizeMb / 1024} GB" else "$currentSizeMb MB")
        },
        leadingContent = {
            Icon(Icons.Default.DataObject, contentDescription = null)
        },
        trailingContent = {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    sizes.forEach { size ->
                        DropdownMenuItem(
                            text = { Text(if (size >= 1024) "${size / 1024} GB" else "$size MB") },
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

@Composable
private fun VideoQualityRow(
    currentQuality: Int,
    onQualityChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val qualities = listOf(144, 240, 360, 480, 720)

    ListItem(
        headlineContent = { Text("Video Playback Quality") },
        supportingContent = { Text("${currentQuality}p") },
        leadingContent = {
            Icon(Icons.Default.GraphicEq, contentDescription = null)
        },
        trailingContent = {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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

@Composable
private fun ContentLanguageRow(
    currentLanguage: String,
    availableLanguages: List<String>,
    onLanguageSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text("Primary Language") },
        supportingContent = {
            Text("$currentLanguage music is prioritized on Home and recommendations")
        },
        leadingContent = {
            Icon(Icons.Default.GraphicEq, contentDescription = null)
        },
        trailingContent = {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    availableLanguages.forEach { language ->
                        DropdownMenuItem(
                            text = { Text(language) },
                            onClick = {
                                onLanguageSelected(language)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun PreferredLanguagesRow(
    selectedLanguages: Set<String>,
    availableLanguages: List<String>,
    onToggleLanguage: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text("Recommendation Languages") },
            supportingContent = {
                Text("Only your selected languages are recommended. Frequent listening in another language can be included automatically.")
            },
            leadingContent = {
                Icon(Icons.Default.Tune, contentDescription = null)
            }
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(availableLanguages) { language ->
                val selected = selectedLanguages.contains(language)
                FilterChip(
                    selected = selected,
                    onClick = { onToggleLanguage(language) },
                    label = { Text(language) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
