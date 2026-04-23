package com.beatloop.music.ui.screens
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.palette.graphics.Palette
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.beatloop.music.data.model.SongItem
import com.beatloop.music.data.model.DownloadState
import com.beatloop.music.ui.LocalPlayerConnection
import com.beatloop.music.ui.components.PremiumFilterChipRow
import com.beatloop.music.ui.components.PremiumGlassSurface
import com.beatloop.music.ui.navigation.Screen
import com.beatloop.music.ui.viewmodel.SongActionsViewModel
import com.beatloop.music.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    navController: NavController? = null,
    onDismiss: (() -> Unit)? = null,
    viewModel: PlayerViewModel = hiltViewModel(),
    songActionsViewModel: SongActionsViewModel = hiltViewModel()
) {
    val playerConnection = LocalPlayerConnection.current
    val uiState by viewModel.uiState.collectAsState()
    val queue by viewModel.queue.collectAsState()
    
    val isPlaying by playerConnection?.isPlaying?.collectAsState() ?: remember { mutableStateOf(false) }
    val currentPosition by playerConnection?.currentPosition?.collectAsState() ?: remember { mutableStateOf(0L) }
    val duration by playerConnection?.duration?.collectAsState() ?: remember { mutableStateOf(0L) }
    val repeatMode by playerConnection?.repeatMode?.collectAsState() ?: remember { mutableStateOf(Player.REPEAT_MODE_OFF) }
    val shuffleModeEnabled by playerConnection?.shuffleModeEnabled?.collectAsState() ?: remember { mutableStateOf(false) }
    val currentQueueIndex by playerConnection?.currentQueueIndex?.collectAsState() ?: remember { mutableStateOf(0) }
    val currentMediaItem by playerConnection?.currentMediaItemFlow?.collectAsState() ?: remember { mutableStateOf(null) }
    val playbackSpeed by playerConnection?.playbackSpeed?.collectAsState() ?: remember { mutableStateOf(1f) }
    
    val playlists by songActionsViewModel.playlists.collectAsState()
    val downloadUiStateMap by songActionsViewModel.downloadUiStateMap.collectAsState()
    val preferredVideoQuality by viewModel.preferredVideoQuality.collectAsState()

    val sheetState = rememberModalBottomSheetState()
    var showQueue by remember { mutableStateOf(false) }
    var activePanel by rememberSaveable { mutableStateOf("Visual") }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showPlaybackSpeedDialog by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var activeSleepTimerMinutes by rememberSaveable { mutableStateOf<Int?>(null) }
    var isVideoMode by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(playerConnection) {
        playerConnection?.let { connection ->
            viewModel.setPlayFromQueueCallback { index ->
                connection.seekToQueueItem(index)
            }
        }
    }

    // Load lyrics, like-state, and queue metadata when song changes.
    LaunchedEffect(currentMediaItem?.mediaId, currentQueueIndex) {
        currentMediaItem?.let { item ->
            val songId = item.mediaId
            val title = item.mediaMetadata.title?.toString() ?: ""
            val artist = item.mediaMetadata.artist?.toString() ?: ""
            viewModel.checkIfLiked(songId)
            viewModel.checkDownloadStatus(songId)
            songActionsViewModel.observeDownload(songId)
            viewModel.loadLyrics(songId, title, artist, (duration / 1000).toInt())
            viewModel.loadVideoVotes(songId)

            playerConnection?.let { connection ->
                viewModel.updateQueue(
                    queue = connection.getQueue().toSongItems(),
                    currentIndex = connection.getCurrentQueueIndex()
                )
            }
        } ?: viewModel.clearLyrics()
    }

    // Extract colors from album art
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val artworkUrl = currentMediaItem?.mediaMetadata?.artworkUri?.toString().toHighResArtworkUrl()
    val dominantColor by produceState(
        initialValue = primaryColor,
        key1 = artworkUrl,
        key2 = currentMediaItem?.mediaId
    ) {
        val fallback = primaryColor
        if (artworkUrl.isNullOrBlank()) {
            value = fallback
            return@produceState
        }

        value = runCatching {
            val request = ImageRequest.Builder(context)
                .data(artworkUrl)
                .allowHardware(false)
                .build()
            val drawable = context.imageLoader.execute(request).drawable ?: return@runCatching fallback
            val bitmap = drawable.toBitmap(width = 96, height = 96)
            val palette = Palette.from(bitmap).clearFilters().generate()
            val colorInt = palette.getDominantColor(fallback.toArgb())
            Color(colorInt)
        }.getOrElse {
            val id = currentMediaItem?.mediaId.orEmpty()
            val hue = (id.hashCode().absoluteValue % 360).toFloat()
            Color.hsl(hue, 0.58f, 0.42f)
        }
    }
    val accentColor = MaterialTheme.colorScheme.primary
    val foregroundColor = MaterialTheme.colorScheme.onBackground
    val mutedForegroundColor = foregroundColor.copy(alpha = 0.74f)
    val currentDownloadUi = currentMediaItem?.mediaId?.let { downloadUiStateMap[it] }
    val isCurrentDownloading = currentDownloadUi?.state == DownloadState.DOWNLOADING
    val isCurrentDownloaded = uiState.isDownloaded || currentDownloadUi?.state == DownloadState.DOWNLOADED
    val currentDownloadProgress = currentDownloadUi?.progress
    val currentFileSizeBytes = currentDownloadUi?.fileSizeBytes ?: uiState.downloadedFileSizeBytes
    var isUserSeeking by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentMediaItem?.mediaId, currentPosition, duration, isUserSeeking) {
        if (!isUserSeeking) {
            sliderPosition = if (duration > 0L) {
                (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }

    fun navigateFromPlayer(route: String) {
        navController?.navigate(route) {
            launchSingleTop = true
        }
        onDismiss?.invoke()
    }

    fun buildPlaybackMediaItem(videoMode: Boolean, quality: Int): MediaItem? {
        val item = currentMediaItem ?: return null
        val mediaId = item.mediaId
        if (mediaId.isBlank()) return null

        val uri = if (videoMode) {
            "beatloop://video/$mediaId?quality=$quality".toUri()
        } else {
            "beatloop://song/$mediaId".toUri()
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(item.mediaMetadata.title)
            .setArtist(item.mediaMetadata.artist)
            .setArtworkUri(item.mediaMetadata.artworkUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun applyPlaybackMode(videoMode: Boolean, quality: Int = preferredVideoQuality) {
        val item = currentMediaItem ?: return
        val currentUri = item.localConfiguration?.uri
        val currentHost = currentUri?.host
        val currentQuality = currentUri?.getQueryParameter("quality")?.toIntOrNull() ?: 360

        val alreadyApplied = if (videoMode) {
            currentHost == "video" && currentQuality == quality
        } else {
            currentHost == "song"
        }

        if (alreadyApplied) return

        buildPlaybackMediaItem(videoMode, quality)?.let { mediaItem ->
            playerConnection?.replaceCurrentMediaItem(mediaItem, preservePosition = true)
        }
    }

    LaunchedEffect(currentMediaItem?.mediaId, isVideoMode, preferredVideoQuality) {
        if (isVideoMode) {
            applyPlaybackMode(videoMode = true, quality = preferredVideoQuality)
        }
    }

    val artworkScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.98f,
        animationSpec = tween(durationMillis = 360),
        label = "player_artwork_scale"
    )

    val playbackModeLabel = if (isVideoMode) {
        "Video mode • ${preferredVideoQuality}p"
    } else {
        "Audio mode"
    }

    val configuration = LocalConfiguration.current
    val isCompactWidth = configuration.screenWidthDp < 360
    val isCompactHeight = configuration.screenHeightDp < 700
    val mediaPanelHorizontalPadding = if (isCompactWidth) 12.dp else 24.dp
    val controlsHorizontalPadding = if (isCompactWidth) 12.dp else 16.dp
    val controlsVerticalPadding = if (isCompactHeight) 8.dp else 10.dp
    val sideControlButtonSize = if (isCompactWidth) 44.dp else 52.dp
    val sideControlIconSize = if (isCompactWidth) 28.dp else 34.dp
    val playButtonSize = if (isCompactWidth) 58.dp else 68.dp
    val playIconSize = if (isCompactWidth) 30.dp else 36.dp
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(86.dp),
            contentScale = ContentScale.Crop,
            alpha = 0.33f
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            dominantColor.copy(alpha = 0.36f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.97f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            PremiumGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        onDismiss?.invoke() ?: navController?.popBackStack()
                    }) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Close",
                            tint = foregroundColor
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Now Playing",
                            style = MaterialTheme.typography.titleSmall,
                            color = foregroundColor
                        )
                        Text(
                            text = playbackModeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedForegroundColor
                        )
                    }

                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showQueue = true
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Queue",
                            tint = foregroundColor
                        )
                    }
                }
            }

            PremiumFilterChipRow(
                items = listOf("Visual", "Lyrics"),
                selectedItem = activePanel,
                onItemSelected = { selected ->
                    activePanel = selected
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = mediaPanelHorizontalPadding, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = activePanel,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "lyrics_transition"
                ) { panelMode ->
                    if (panelMode == "Lyrics") {
                        LyricsView(
                            lyrics = uiState.lyrics,
                            isLoading = uiState.isLoadingLyrics,
                            errorMessage = uiState.lyricsError,
                            currentPositionMs = currentPosition,
                            onSeekToLine = { timestampMs ->
                                playerConnection?.seekTo(timestampMs)
                            }
                        )
                    } else {
                        if (isVideoMode) {
                            PremiumGlassSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f),
                                shape = RoundedCornerShape(20.dp),
                                tonalElevation = 4.dp
                            ) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            useController = false
                                            player = playerConnection?.player
                                        }
                                    },
                                    update = { view ->
                                        view.player = playerConnection?.player
                                    }
                                )
                            }
                        } else {
                            AsyncImage(
                                model = artworkUrl,
                                contentDescription = "Album Art",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .scale(artworkScale)
                                    .clip(RoundedCornerShape(28.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }

            PremiumGlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = controlsHorizontalPadding, vertical = controlsVerticalPadding)
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(26.dp),
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = if (isCompactWidth) 12.dp else 16.dp,
                        vertical = if (isCompactHeight) 10.dp else 14.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentMediaItem?.mediaMetadata?.title?.toString() ?: "Unknown",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = foregroundColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown",
                                style = MaterialTheme.typography.bodyLarge,
                                color = mutedForegroundColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            when {
                                isCurrentDownloaded -> {
                                    Text(
                                        text = if (currentFileSizeBytes != null) {
                                            "Downloaded • ${formatFileSize(currentFileSizeBytes)}"
                                        } else {
                                            "Downloaded"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = accentColor
                                    )
                                }

                                isCurrentDownloading -> {
                                    val percent = currentDownloadProgress?.coerceIn(0, 100)
                                    Text(
                                        text = if (percent != null) {
                                            "Downloading • $percent%"
                                        } else {
                                            "Downloading..."
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = accentColor
                                    )
                                }
                            }
                        }

                        FilledTonalIconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.toggleLike(currentMediaItem?.mediaId ?: "")
                            }
                        ) {
                            Icon(
                                imageVector = if (uiState.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (uiState.isLiked) accentColor else foregroundColor
                            )
                        }
                    }

                    if (uiState.videoVotes != null || uiState.isLoadingVideoVotes) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (uiState.isLoadingVideoVotes) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = mutedForegroundColor
                                )
                                Text(
                                    text = "Fetching video stats",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = mutedForegroundColor
                                )
                            } else {
                                uiState.videoVotes?.let { votes ->
                                    Icon(
                                        imageVector = Icons.Default.ThumbUp,
                                        contentDescription = "Likes",
                                        tint = mutedForegroundColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = formatCompactCount(votes.likes),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = foregroundColor.copy(alpha = 0.9f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ThumbDown,
                                        contentDescription = "Dislikes",
                                        tint = mutedForegroundColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = formatCompactCount(votes.dislikes),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = foregroundColor.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDuration(currentPosition),
                            style = MaterialTheme.typography.labelMedium,
                            color = mutedForegroundColor
                        )
                        Text(
                            text = formatDuration(duration),
                            style = MaterialTheme.typography.labelMedium,
                            color = mutedForegroundColor
                        )
                    }

                    Slider(
                        value = sliderPosition,
                        onValueChange = { value ->
                            isUserSeeking = true
                            sliderPosition = value
                        },
                        onValueChangeFinished = {
                            val targetPosition = if (duration > 0L) {
                                (sliderPosition * duration.toFloat()).toLong()
                            } else {
                                0L
                            }
                            playerConnection?.seekTo(targetPosition)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isUserSeeking = false
                        },
                        modifier = Modifier.padding(top = 2.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            playerConnection?.toggleShuffle()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (shuffleModeEnabled) accentColor else mutedForegroundColor
                            )
                        }

                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                playerConnection?.skipToPrevious()
                            },
                            modifier = Modifier.size(sideControlButtonSize)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = foregroundColor,
                                modifier = Modifier.size(sideControlIconSize)
                            )
                        }

                        FloatingActionButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                playerConnection?.togglePlayPause()
                            },
                            modifier = Modifier.size(playButtonSize),
                            containerColor = accentColor,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(playIconSize)
                            )
                        }

                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                playerConnection?.skipToNext()
                            },
                            modifier = Modifier.size(sideControlButtonSize)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = foregroundColor,
                                modifier = Modifier.size(sideControlIconSize)
                            )
                        }

                        IconButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            playerConnection?.cycleRepeatMode()
                        }) {
                            Icon(
                                imageVector = when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                    else -> Icons.Default.Repeat
                                },
                                contentDescription = "Repeat",
                                tint = if (repeatMode != Player.REPEAT_MODE_OFF) {
                                    accentColor
                                } else {
                                    mutedForegroundColor
                                }
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (isCompactWidth) 2.dp else 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            IconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    activePanel = if (activePanel == "Lyrics") "Visual" else "Lyrics"
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lyrics,
                                    contentDescription = "Lyrics",
                                    tint = if (activePanel == "Lyrics") accentColor else mutedForegroundColor
                                )
                            }
                        }

                        item {
                            IconButton(onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showQueue = true
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = "Queue",
                                    tint = mutedForegroundColor
                                )
                            }
                        }

                        item {
                            IconButton(onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showSleepTimerDialog = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Sleep Timer",
                                    tint = if (activeSleepTimerMinutes != null) accentColor else mutedForegroundColor
                                )
                            }
                        }

                        item {
                            IconButton(onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showPlaybackSpeedDialog = true
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Playback speed",
                                    tint = if ((playbackSpeed - 1f).absoluteValue < 0.01f) {
                                        mutedForegroundColor
                                    } else {
                                        accentColor
                                    }
                                )
                            }
                        }

                        item {
                            IconButton(
                                enabled = !isCurrentDownloaded && !isCurrentDownloading,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentMediaItem?.let { item ->
                                        songActionsViewModel.downloadSong(
                                            SongItem(
                                                id = item.mediaId,
                                                title = item.mediaMetadata.title?.toString() ?: "Unknown",
                                                artistsText = item.mediaMetadata.artist?.toString() ?: "Unknown",
                                                thumbnailUrl = item.mediaMetadata.artworkUri?.toString()
                                            ),
                                            downloadVideo = isVideoMode,
                                            videoQuality = preferredVideoQuality
                                        )
                                        navigateFromPlayer(Screen.Downloads.route)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isCurrentDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                                    contentDescription = "Download",
                                    tint = when {
                                        isCurrentDownloaded -> accentColor
                                        isCurrentDownloading -> accentColor
                                        else -> mutedForegroundColor
                                    }
                                )
                            }
                        }

                        item {
                            IconButton(onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showAddToPlaylist = true
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                    contentDescription = "Add to Playlist",
                                    tint = mutedForegroundColor
                                )
                            }
                        }

                        item {
                            IconButton(onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentMediaItem?.let { item ->
                                    val song = SongItem(
                                        id = item.mediaId,
                                        title = item.mediaMetadata.title?.toString() ?: "Unknown",
                                        artistsText = item.mediaMetadata.artist?.toString() ?: "Unknown",
                                        thumbnailUrl = item.mediaMetadata.artworkUri?.toString()
                                    )
                                    context.startActivity(songActionsViewModel.createShareIntent(song))
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = mutedForegroundColor
                                )
                            }
                        }

                        item {
                            IconButton(onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isVideoMode = !isVideoMode
                                applyPlaybackMode(videoMode = isVideoMode, quality = preferredVideoQuality)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.OndemandVideo,
                                    contentDescription = "Video mode",
                                    tint = if (isVideoMode) accentColor else mutedForegroundColor
                                )
                            }
                        }
                    }
            }
        }
    }
    }


    // Queue Bottom Sheet
    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = {
                showQueue = false
            },
            sheetState = sheetState
        ) {
            QueueView(
                queue = queue,
                currentIndex = uiState.currentQueueIndex,
                onItemClick = { index ->
                    playerConnection?.seekToQueueItem(index)
                    playerConnection?.let { connection ->
                        viewModel.updateQueue(connection.getQueue().toSongItems(), connection.getCurrentQueueIndex())
                    }
                },
                onRemoveItem = { index ->
                    playerConnection?.removeQueueItem(index)
                    playerConnection?.let { connection ->
                        viewModel.updateQueue(connection.getQueue().toSongItems(), connection.getCurrentQueueIndex())
                    }
                }
            )
        }
    }

    if (showSleepTimerDialog) {
        val options = listOf(10, 15, 30, 45, 60)
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = {
                Text(text = "Sleep Timer")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    options.forEach { minutes ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                playerConnection?.setSleepTimer(minutes)
                                activeSleepTimerMinutes = minutes
                                showSleepTimerDialog = false
                            }
                        ) {
                            Text(text = "Stop playback in $minutes minutes")
                        }
                    }
                    if (activeSleepTimerMinutes != null) {
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                playerConnection?.clearSleepTimer()
                                activeSleepTimerMinutes = null
                                showSleepTimerDialog = false
                            }
                        ) {
                            Text(text = "Clear sleep timer")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showPlaybackSpeedDialog) {
        val speedOptions = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        AlertDialog(
            onDismissRequest = { showPlaybackSpeedDialog = false },
            title = { Text("Playback Speed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Current speed: ${formatPlaybackSpeed(playbackSpeed)}x",
                        style = MaterialTheme.typography.labelLarge
                    )
                    speedOptions.forEach { speed ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                playerConnection?.setPlaybackSpeed(speed)
                                showPlaybackSpeedDialog = false
                            }
                        ) {
                            Text(text = "${formatPlaybackSpeed(speed)}x")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaybackSpeedDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showAddToPlaylist && currentMediaItem != null) {
        val songItem = SongItem(
            id = currentMediaItem!!.mediaId,
            title = currentMediaItem!!.mediaMetadata.title?.toString() ?: "Unknown",
            artistsText = currentMediaItem!!.mediaMetadata.artist?.toString() ?: "Unknown",
            thumbnailUrl = currentMediaItem!!.mediaMetadata.artworkUri?.toString()
        )
        com.beatloop.music.ui.components.AddToPlaylistBottomSheet(
            playlists = playlists,
            onDismiss = { showAddToPlaylist = false },
            onCreateNew = {
                showAddToPlaylist = false
                showCreatePlaylist = true
            },
            onSelectPlaylist = { playlistId ->
                songActionsViewModel.addToPlaylist(playlistId, songItem, context)
                showAddToPlaylist = false
            }
        )
    }

    if (showCreatePlaylist && currentMediaItem != null) {
        var playlistName by remember { mutableStateOf("") }
        val songItem = SongItem(
            id = currentMediaItem!!.mediaId,
            title = currentMediaItem!!.mediaMetadata.title?.toString() ?: "Unknown",
            artistsText = currentMediaItem!!.mediaMetadata.artist?.toString() ?: "Unknown",
            thumbnailUrl = currentMediaItem!!.mediaMetadata.artworkUri?.toString()
        )
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            title = { Text("Create Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            songActionsViewModel.createPlaylistAndAddSong(playlistName, songItem, context)
                            showCreatePlaylist = false
                        }
                    },
                    enabled = playlistName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylist = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun LyricsView(
    lyrics: com.beatloop.music.data.model.Lyrics?,
    isLoading: Boolean,
    errorMessage: String?,
    currentPositionMs: Long,
    onSeekToLine: (Long) -> Unit
) {
    val foregroundColor = MaterialTheme.colorScheme.onBackground
    val mutedForegroundColor = foregroundColor.copy(alpha = 0.62f)

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (errorMessage != null || lyrics == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Lyrics not available for this track",
                style = MaterialTheme.typography.bodyLarge,
                color = foregroundColor.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // Find current line index
    val currentLineIndex = remember(currentPositionMs, lyrics.lines) {
        if (lyrics.synced) {
            lyrics.lines.indexOfLast { line ->
                line.startTime <= currentPositionMs
            }.coerceAtLeast(0)
        } else {
            0
        }
    }
    
    // Auto-scroll to current line
    LaunchedEffect(currentLineIndex) {
        if (lyrics.synced && currentLineIndex >= 0) {
            scope.launch {
                listState.animateScrollToItem(
                    index = currentLineIndex,
                    scrollOffset = -200
                )
            }
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 48.dp)
    ) {
        item {
            val sourceLabel = lyrics.source ?: "Unknown source"
            val modeLabel = if (lyrics.synced) "Synced" else "Plain"

            Text(
                text = "$sourceLabel • $modeLabel",
                style = MaterialTheme.typography.labelLarge,
                color = mutedForegroundColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            )

            if (lyrics.synced) {
                Text(
                    text = "Tap any line to seek",
                    style = MaterialTheme.typography.labelMedium,
                    color = foregroundColor.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                )
            }
        }

        if (lyrics.synced) {
            itemsIndexed(lyrics.lines) { index, line ->
                val isCurrentLine = index == currentLineIndex
                
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrentLine) foregroundColor else foregroundColor.copy(alpha = 0.42f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSeekToLine(line.startTime) }
                        .padding(vertical = 8.dp, horizontal = 16.dp)
                        .animateContentSize()
                )
            }
        } else {
            // Plain lyrics
            item {
                Text(
                    text = lyrics.plainText ?: lyrics.lines.joinToString("\n") { it.text },
                    style = MaterialTheme.typography.bodyLarge,
                    color = foregroundColor.copy(alpha = 0.84f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
}

private fun List<MediaItem>.toSongItems(): List<SongItem> {
    return map { mediaItem ->
        val uri = mediaItem.localConfiguration?.uri
        SongItem(
            id = mediaItem.mediaId,
            title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown",
            artistsText = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown",
            thumbnailUrl = mediaItem.mediaMetadata.artworkUri?.toString(),
            localPath = if (uri?.scheme == "file") uri.path else null
        )
    }
}

@Composable
private fun QueueView(
    queue: List<com.beatloop.music.data.model.SongItem>,
    currentIndex: Int,
    onItemClick: (Int) -> Unit,
    onRemoveItem: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Queue",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )
        
        if (queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Queue is empty",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                itemsIndexed(queue) { index, song ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = song.title,
                                fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
                                color = if (index == currentIndex) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurface
                            )
                        },
                        supportingContent = { Text(song.artistsText) },
                        leadingContent = {
                            if (index == currentIndex) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Now Playing",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                AsyncImage(
                                    model = song.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { onRemoveItem(index) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove"
                                )
                            }
                        },
                        modifier = Modifier.clickable { onItemClick(index) }
                    )
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatCompactCount(value: Long): String {
    return when {
        value >= 1_000_000_000L -> String.format("%.1fB", value / 1_000_000_000.0)
        value >= 1_000_000L -> String.format("%.1fM", value / 1_000_000.0)
        value >= 1_000L -> String.format("%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}

private fun formatPlaybackSpeed(speed: Float): String {
    val rounded = ((speed * 100).toInt()) / 100f
    val isWholeNumber = (rounded - rounded.toInt().toFloat()).absoluteValue < 0.01f
    return if (isWholeNumber) {
        rounded.toInt().toString()
    } else {
        "%.2f".format(rounded).trimEnd('0').trimEnd('.')
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format("%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}

private fun String?.toHighResArtworkUrl(): String? {
    if (this.isNullOrBlank()) return this
    return this
        .replace(Regex("=w\\d+-h\\d+"), "=w1200-h1200")
        .replace(Regex("=s\\d+"), "=s1200")
}
