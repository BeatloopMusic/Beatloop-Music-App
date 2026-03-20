package com.beatloop.music.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showCelebration by rememberSaveable { mutableStateOf(false) }

    val onOnboardingCompleted: () -> Unit = {
        showCelebration = true
        scope.launch {
            delay(1450)
            onComplete()
        }
    }

    val progressValue = when (state.step) {
        OnboardingStep.Languages -> 0.25f
        OnboardingStep.Singers -> 0.5f
        OnboardingStep.Lyricists -> 0.75f
        OnboardingStep.Directors -> 1f
    }

    val stepLabel = when (state.step) {
        OnboardingStep.Languages -> "Step 1 of 4"
        OnboardingStep.Singers -> "Step 2 of 4"
        OnboardingStep.Lyricists -> "Step 3 of 4"
        OnboardingStep.Directors -> "Step 4 of 4"
    }

    val continueLabel = when (state.step) {
        OnboardingStep.Languages -> "Continue to Singers"
        OnboardingStep.Singers -> "Continue to Lyricists"
        OnboardingStep.Lyricists -> "Continue to Directors"
        OnboardingStep.Directors -> "Start Listening"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { viewModel.goBack() },
                    enabled = state.step != OnboardingStep.Languages && !showCelebration
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }

                Text(
                    text = "Personalize Beatloop",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(40.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stepLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progressValue },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(18.dp))

            AnimatedContent(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                targetState = state.step,
                transitionSpec = {
                    (slideInVertically(
                        animationSpec = tween(300),
                        initialOffsetY = { it / 5 }
                    ) + fadeIn(animationSpec = tween(260))).togetherWith(
                        slideOutVertically(
                            animationSpec = tween(180),
                            targetOffsetY = { -it / 6 }
                        ) + fadeOut(animationSpec = tween(160))
                    )
                },
                label = "onboarding_step_transition"
            ) { step ->
                when (step) {
                    OnboardingStep.Languages -> {
                        LanguageStep(
                            selected = state.selectedLanguages,
                            onToggle = viewModel::toggleLanguage
                        )
                    }

                    OnboardingStep.Singers -> {
                        SingerStep(
                            selected = state.selectedSingers,
                            options = OnboardingCatalog.singersForLanguages(state.selectedLanguages),
                            onToggle = viewModel::toggleSinger
                        )
                    }

                    OnboardingStep.Lyricists -> {
                        ChoicePillStep(
                            title = "Choose Your Favorite Lyricists",
                            subtitle = "Pick at least one lyricist. You can also choose None.",
                            selected = state.selectedLyricists,
                            options = OnboardingCatalog.lyricistsForLanguages(state.selectedLanguages).map { it.name },
                            onToggle = viewModel::toggleLyricist
                        )
                    }

                    OnboardingStep.Directors -> {
                        ChoicePillStep(
                            title = "Choose Music Directors",
                            subtitle = "Pick at least one music director to tune your recommendations.",
                            selected = state.selectedDirectors,
                            options = OnboardingCatalog.musicDirectorsForLanguages(state.selectedLanguages).map { it.name },
                            onToggle = viewModel::toggleDirector
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            AnimatedVisibility(
                visible = !state.errorMessage.isNullOrBlank(),
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f)
            ) {
                state.errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.proceed(onOnboardingCompleted) },
                enabled = !showCelebration,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = continueLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        CelebrationOverlay(visible = showCelebration)
    }
}

@Composable
private fun LanguageStep(
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Choose Languages",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Select at least one language. Indian languages are listed first.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = "Indian Languages",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            item { Spacer(modifier = Modifier.height(0.dp)) }

            items(OnboardingCatalog.indianLanguages) { language ->
                AnimatedFilterChip(
                    text = language,
                    selected = language in selected,
                    onClick = { onToggle(language) }
                )
            }

            item {
                Text(
                    text = "International Languages",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            item { Spacer(modifier = Modifier.height(0.dp)) }

            items(OnboardingCatalog.internationalLanguages) { language ->
                AnimatedFilterChip(
                    text = language,
                    selected = language in selected,
                    onClick = { onToggle(language) }
                )
            }
        }
    }
}

@Composable
private fun SingerStep(
    selected: Set<String>,
    options: List<OnboardingArtistOption>,
    onToggle: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Select Your Favorite Singers",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Pick at least one singer. We will prioritize these voices in recommendations.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(
            targetState = options,
            transitionSpec = {
                (fadeIn(animationSpec = tween(220)) + scaleIn(
                    initialScale = 0.98f,
                    animationSpec = tween(220)
                )).togetherWith(
                    fadeOut(animationSpec = tween(130))
                )
            },
            label = "singer_options_transition"
        ) { animatedOptions ->
            if (animatedOptions.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Choose languages first to unlock singer recommendations.",
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(animatedOptions, key = { it.name }) { singer ->
                        val isSelected = singer.name in selected
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        val cardScale by animateFloatAsState(
                            targetValue = when {
                                pressed -> 0.96f
                                isSelected -> 1.02f
                                else -> 1f
                            },
                            animationSpec = tween(140),
                            label = "singer_card_scale"
                        )

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            tonalElevation = if (isSelected) 6.dp else 1.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = cardScale
                                    scaleY = cardScale
                                }
                                .clickable(interactionSource = interaction, indication = null) {
                                    onToggle(singer.name)
                                }
                        ) {
                            var useFallbackImage by remember(singer.imageUrl, singer.name) { mutableStateOf(false) }

                            Column(modifier = Modifier.padding(12.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(112.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(96.dp)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outlineVariant
                                                },
                                                shape = CircleShape
                                            )
                                            .padding(3.dp)
                                    ) {
                                        AsyncImage(
                                            model = if (useFallbackImage) {
                                                OnboardingCatalog.singerFallbackUrl(singer.name)
                                            } else {
                                                OnboardingCatalog.singerPortraitUrl(singer.imageUrl)
                                            },
                                            contentDescription = singer.name,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop,
                                            onError = {
                                                if (!useFallbackImage) {
                                                    useFallbackImage = true
                                                }
                                            }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = singer.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Top track: ${singer.bestSong}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoicePillStep(
    title: String,
    subtitle: String,
    selected: Set<String>,
    options: List<String>,
    onToggle: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(
            targetState = options,
            transitionSpec = {
                (fadeIn(animationSpec = tween(200)) + scaleIn(
                    initialScale = 0.98f,
                    animationSpec = tween(200)
                )).togetherWith(
                    fadeOut(animationSpec = tween(130))
                )
            },
            label = "choice_options_transition"
        ) { animatedOptions ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(animatedOptions, key = { it }) { option ->
                    AnimatedFilterChip(
                        text = option,
                        selected = option in selected,
                        onClick = { onToggle(option) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CelebrationOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(260)) + scaleIn(initialScale = 0.94f),
        exit = fadeOut(animationSpec = tween(160))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
                        )
                    )
                )
        ) {
            val infinite = rememberInfiniteTransition(label = "celebration_transition")
            val pulse by infinite.animateFloat(
                initialValue = 0.96f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "celebration_pulse"
            )
            val orbit by infinite.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "celebration_orbit"
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(230.dp)
                    .graphicsLayer {
                        rotationZ = orbit
                    }
            ) {
                val orbitPoints = listOf(
                    (-78).dp to (-12).dp,
                    (-42).dp to (-72).dp,
                    35.dp to (-72).dp,
                    78.dp to (-10).dp,
                    44.dp to 70.dp,
                    (-38).dp to 72.dp
                )
                orbitPoints.forEachIndexed { index, (x, y) ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = x, y = y)
                            .size(if (index % 2 == 0) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                when (index % 3) {
                                    0 -> MaterialTheme.colorScheme.primary
                                    1 -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.tertiary
                                }
                            )
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(150.dp)
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                            shape = CircleShape
                        )
                )
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 10.dp,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 22.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp)
                ) {
                    Text(
                        text = "You are all set",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Building your first personalized mix...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.96f
            selected -> 1.02f
            else -> 1f
        },
        animationSpec = tween(120),
        label = "chip_scale"
    )

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        interactionSource = interaction,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    )
}
