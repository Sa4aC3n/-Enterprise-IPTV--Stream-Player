package com.enterprise.iptv.presentation.channels

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.enterprise.iptv.R
import com.enterprise.iptv.domain.model.Channel
import com.enterprise.iptv.domain.recorder.RecordState
import com.enterprise.iptv.presentation.player.PlaybackStatus
import com.enterprise.iptv.presentation.player.PlayerEngine
import com.enterprise.iptv.presentation.theme.AccentTeal
import com.enterprise.iptv.presentation.theme.DarkSurface
import com.enterprise.iptv.presentation.theme.LiveRed

@OptIn(UnstableApi::class)
@Composable
fun ChannelScreen(
    viewModel: ChannelsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_playlist))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = Color.White,
                    actionIconContentColor = AccentTeal
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is ChannelsUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentTeal)
                    }
                }
                is ChannelsUiState.Success -> {
                    AdaptiveContent(
                        state = state,
                        playerEngine = viewModel.playerEngine,
                        onChannelSelect = { viewModel.onChannelSelect(it) },
                        onFavoriteToggle = { viewModel.toggleFavorite(it) },
                        onCategorySelect = { viewModel.onCategorySelect(it) },
                        onCountrySelect = { viewModel.onCountrySelect(it) },
                        onSearchChange = { viewModel.onSearchQueryChange(it) },
                        onStartRecord = { viewModel.startRecording() },
                        onStopRecord = { viewModel.stopRecording() }
                    )
                }
                is ChannelsUiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (showAddDialog) {
                AddPlaylistDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { url ->
                        viewModel.importM3uPlaylist(url)
                        showAddDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun AdaptiveContent(
    state: ChannelsUiState.Success,
    playerEngine: PlayerEngine,
    onChannelSelect: (Channel) -> Unit,
    onFavoriteToggle: (Channel) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onCountrySelect: (String?) -> Unit,
    onSearchChange: (String) -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    if (isTablet) {
        // Tablet Side-by-Side (Master-Detail) Layout
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
                    .background(Color.Black)
            ) {
                VideoPlayerContainer(playerEngine = playerEngine, status = state.playbackStatus)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                RecordingBar(
                    recordState = state.recordState,
                    hasChannel = state.currentChannel != null,
                    onStart = onStartRecord,
                    onStop = onStopRecord
                )
                SearchBar(query = state.searchQuery, onQueryChange = onSearchChange)
                FilterSections(
                    categories = state.categories,
                    selectedCategory = state.selectedCategory,
                    countries = state.countries,
                    selectedCountry = state.selectedCountry,
                    onCategorySelect = onCategorySelect,
                    onCountrySelect = onCountrySelect
                )
                ChannelsList(
                    channels = state.channels,
                    currentChannel = state.currentChannel,
                    onChannelSelect = onChannelSelect,
                    onFavoriteToggle = onFavoriteToggle
                )
            }
        }
    } else {
        // Phone Vertical Layout
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .background(Color.Black)
            ) {
                VideoPlayerContainer(playerEngine = playerEngine, status = state.playbackStatus)
            }
            RecordingBar(
                recordState = state.recordState,
                hasChannel = state.currentChannel != null,
                onStart = onStartRecord,
                onStop = onStopRecord
            )
            SearchBar(query = state.searchQuery, onQueryChange = onSearchChange)
            FilterSections(
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                countries = state.countries,
                selectedCountry = state.selectedCountry,
                onCategorySelect = onCategorySelect,
                onCountrySelect = onCountrySelect
            )
            ChannelsList(
                channels = state.channels,
                currentChannel = state.currentChannel,
                onChannelSelect = onChannelSelect,
                onFavoriteToggle = onFavoriteToggle
            )
        }
    }
}

@Composable
fun VideoPlayerContainer(
    playerEngine: PlayerEngine,
    status: PlaybackStatus
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = playerEngine.exoPlayer
                    useController = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (status is PlaybackStatus.Buffering) {
            CircularProgressIndicator(color = AccentTeal)
        } else if (status is PlaybackStatus.Error) {
            Text(
                text = stringResource(R.string.error_stream_failed),
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun RecordingBar(
    recordState: RecordState,
    hasChannel: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val isRecording = recordState is RecordState.Recording

    Surface(
        tonalElevation = 3.dp,
        color = DarkSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = if (isRecording) LiveRed else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                if (recordState is RecordState.Recording) {
                    val minutes = recordState.durationSeconds / 60
                    val seconds = recordState.durationSeconds % 60
                    val sizeMb = recordState.bytesRecorded / (1024 * 1024)
                    Text(
                        text = "REC %02d:%02d (${sizeMb}MB)".format(minutes, seconds),
                        color = LiveRed,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = stringResource(R.string.recording_ready),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                }
            }

            Button(
                onClick = { if (isRecording) onStop() else onStart() },
                enabled = hasChannel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) LiveRed else AccentTeal,
                    contentColor = if (isRecording) Color.White else Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isRecording) stringResource(R.string.btn_stop_record) else stringResource(R.string.btn_start_record),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
fun FilterSections(
    categories: List<String>,
    selectedCategory: String?,
    countries: List<String>,
    selectedCountry: String?,
    onCategorySelect: (String?) -> Unit,
    onCountrySelect: (String?) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        // Categories Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategorySelect(null) },
                    label = { Text(stringResource(R.string.all_categories)) }
                )
            }
            items(categories) { category ->
                FilterChip(
                    selected = category == selectedCategory,
                    onClick = { onCategorySelect(category) },
                    label = { Text(category) }
                )
            }
        }

        // Countries Row (if countries exist)
        if (countries.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(countries) { country ->
                    SuggestionChip(
                        onClick = { onCountrySelect(country) },
                        label = { Text(country, style = MaterialTheme.typography.labelSmall) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (country == selectedCountry) AccentTeal.copy(alpha = 0.3f) else Color.Transparent
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelsList(
    channels: List<Channel>,
    currentChannel: Channel?,
    onChannelSelect: (Channel) -> Unit,
    onFavoriteToggle: (Channel) -> Unit
) {
    if (channels.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_channels_found),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(channels, key = { it.id }) { channel ->
                val isSelected = channel.id == currentChannel?.id
                ListItem(
                    headlineContent = {
                        Text(
                            text = channel.name,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) AccentTeal else Color.White
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "${channel.groupTitle} ${channel.countryCode?.let { "• $it" } ?: ""}",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingContent = {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurface)
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { onFavoriteToggle(channel) }) {
                            Icon(
                                imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (channel.isFavorite) LiveRed else Color.Gray
                            )
                        }
                    },
                    modifier = Modifier
                        .clickable { onChannelSelect(channel) }
                        .background(if (isSelected) AccentTeal.copy(alpha = 0.08f) else Color.Transparent)
                )
                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
fun AddPlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var urlText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_playlist)) },
        text = {
            Column {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text(stringResource(R.string.playlist_url_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (urlText.isNotBlank()) onConfirm(urlText.trim()) },
                enabled = urlText.isNotBlank()
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
