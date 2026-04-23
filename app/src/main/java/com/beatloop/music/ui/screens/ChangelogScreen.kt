package com.beatloop.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.beatloop.music.ui.components.PremiumGlassSurface
import com.beatloop.music.ui.components.PremiumScreenBackground

private sealed class ChangelogRow {
    data class Header(val text: String) : ChangelogRow()
    data class SubHeader(val text: String) : ChangelogRow()
    data class Bullet(val text: String) : ChangelogRow()
    data class Paragraph(val text: String) : ChangelogRow()
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ChangelogScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var loadingError by remember { mutableStateOf<String?>(null) }

    val changelogText by produceState(initialValue = "") {
        value = runCatching {
            context.assets.open("changelog.md").bufferedReader().use { it.readText() }
        }.onFailure { error ->
            loadingError = error.message ?: "Unable to load changelog"
        }.getOrDefault("")
    }

    val rows = remember(changelogText) { parseChangelog(changelogText) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Changelog",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        PremiumScreenBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PremiumGlassSurface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (changelogText.isBlank() && loadingError == null) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (loadingError != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = loadingError ?: "Unable to load changelog",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(rows) { row ->
                            when (row) {
                                is ChangelogRow.Header -> {
                                    Text(
                                        text = row.text,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                is ChangelogRow.SubHeader -> {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = row.text,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                is ChangelogRow.Bullet -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "-",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 1.dp)
                                        )
                                        Text(
                                            text = row.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .padding(start = 8.dp)
                                                .weight(1f)
                                        )
                                    }
                                }

                                is ChangelogRow.Paragraph -> {
                                    Text(
                                        text = row.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseChangelog(raw: String): List<ChangelogRow> {
    if (raw.isBlank()) return emptyList()

    val rows = mutableListOf<ChangelogRow>()
    raw.lineSequence().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.isBlank() -> Unit
            trimmed.startsWith("### ") -> rows += ChangelogRow.SubHeader(trimmed.removePrefix("### ").trim())
            trimmed.startsWith("## ") -> rows += ChangelogRow.Header(trimmed.removePrefix("## ").trim())
            trimmed.startsWith("# ") -> rows += ChangelogRow.Header(trimmed.removePrefix("# ").trim())
            trimmed.startsWith("- ") -> rows += ChangelogRow.Bullet(trimmed.removePrefix("- ").trim())
            else -> rows += ChangelogRow.Paragraph(trimmed)
        }
    }

    return rows
}
