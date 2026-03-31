package com.beatloop.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

object PremiumSpacing {
    val screenHorizontal = 16.dp
    val sectionVertical = 14.dp
    val cardRadius = 22.dp
    val heroRadius = 28.dp
}

@Composable
fun PremiumScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
        )
        content()
    }
}

@Composable
fun PremiumGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(PremiumSpacing.cardRadius),
    tonalElevation: androidx.compose.ui.unit.Dp = 2.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
        ),
        tonalElevation = tonalElevation,
        shadowElevation = tonalElevation * 0.6f
    ) {
        content()
    }
}

@Composable
fun PremiumSectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PremiumSpacing.screenHorizontal, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!actionLabel.isNullOrBlank() && onAction != null) {
            TextButton(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
fun PremiumHeroCard(
    title: String,
    subtitle: String,
    badge: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    PremiumGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PremiumSpacing.screenHorizontal)
            .clip(RoundedCornerShape(PremiumSpacing.heroRadius))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(PremiumSpacing.heroRadius),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PremiumFilterChipRow(
    items: List<String>,
    selectedItem: String,
    modifier: Modifier = Modifier,
    onItemSelected: (String) -> Unit
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = PremiumSpacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { item ->
            val selected = selectedItem == item
            val scale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.96f,
                animationSpec = tween(220),
                label = "filter_chip_scale"
            )
            FilterChip(
                selected = selected,
                onClick = { onItemSelected(item) },
                label = {
                    Text(
                        text = item,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier.scale(scale),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                )
            )
        }
    }
}

@Composable
fun PremiumSkeletonHero(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PremiumSpacing.screenHorizontal)
            .height(160.dp)
            .clip(RoundedCornerShape(PremiumSpacing.heroRadius))
            .background(brush)
    )
}

@Composable
fun PremiumSkeletonCard(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Column(
        modifier = modifier.width(170.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .height(14.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.56f)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(brush)
        )
    }
}

@Composable
fun PremiumSkeletonListItem(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PremiumSpacing.screenHorizontal, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(brush)
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.44f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
fun PremiumEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.SignalWifiOff,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        PremiumGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PremiumSpacing.screenHorizontal),
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (!actionLabel.isNullOrBlank() && onAction != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    ElevatedButton(
                        onClick = onAction,
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    PremiumEmptyState(
        title = "Something interrupted the session",
        message = message,
        modifier = modifier,
        icon = Icons.Default.ErrorOutline,
        actionLabel = if (onRetry != null) "Try again" else null,
        onAction = onRetry
    )
}

@Composable
fun PremiumOfflineBanner(
    isOnlineRecovery: Boolean,
    modifier: Modifier = Modifier
) {
    val container = if (isOnlineRecovery) {
        Color(0xFF1E8E5A)
    } else {
        MaterialTheme.colorScheme.error
    }
    PremiumGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PremiumSpacing.screenHorizontal, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(container.copy(alpha = 0.92f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isOnlineRecovery) Icons.Default.SignalWifiOff else Icons.Default.SignalWifiOff,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = if (isOnlineRecovery) "Back online" else "Offline mode",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
        }
    }
}
