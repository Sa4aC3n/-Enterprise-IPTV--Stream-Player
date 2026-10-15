package com.enterprise.iptv.presentation.channels

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.enterprise.iptv.presentation.player.VideoQuality
import com.enterprise.iptv.presentation.theme.AccentTeal
import com.enterprise.iptv.presentation.theme.DarkSurface
import com.enterprise.iptv.presentation.theme.LiveRed

/**
 * تحويل كود الدولة المكون من حرفين (ISO-3166-1 alpha-2) إلى إيموجي علم الدولة مباشرة
 */
fun getCountryFlagEmoji(countryCode: String?): String {
    if (countryCode.isNullOrBlank() || countryCode.trim().length != 2) return "🌐"
    val upper = countryCode.trim().uppercase()
    return try {
        val firstChar = Character.toChars(0x1F1E6 + (upper[0] - 'A'))
        val secondChar = Character.toChars(0x1F1E6 + (upper[1] - 'A'))
        String(firstChar) + String(secondChar)
    } catch (e: Exception) {
        "🌐"
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ChannelScreen(
    viewModel: ChannelsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isFullscreen by viewModel.isFullscreen.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    // الرجوع من وضع ملء الشاشة بزر الرجوع في الهاتف
    BackHandler(enabled = isFullscreen) {
        viewModel.setFullscreen(false)
    }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
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
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullscreen) PaddingValues(0.dp) else paddingValues)
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
                        isFullscreen = isFullscreen,
                        onToggleFullscreen = { viewModel.toggleFullscreen() },
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
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
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

    // في وضع ملء الشاشة يملأ المشغل كامل الشاشة
    if (isFullscreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            VideoPlayerContainer(
                playerEngine = playerEngine,
                status = state.playbackStatus,
                channelName = state.currentChannel?.name ?: "",
                isFullscreen = true,
                onToggleFullscreen = onToggleFullscreen
            )
        }
        return
    }

    if (isTablet) {
        // Tablet Side-by-Side (Master-Detail) Layout
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
                    .background(Color.Black)
            ) {
                VideoPlayerContainer(
                    playerEngine = playerEngine,
                    status = state.playbackStatus,
                    channelName = state.currentChannel?.name ?: "",
                    isFullscreen = false,
                    onToggleFullscreen = onToggleFullscreen
                )
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
                DualFilterDropdowns(
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
                    .height(240.dp)
                    .background(Color.Black)
            ) {
                VideoPlayerContainer(
                    playerEngine = playerEngine,
                    status = state.playbackStatus,
                    channelName = state.currentChannel?.name ?: "",
                    isFullscreen = false,
                    onToggleFullscreen = onToggleFullscreen
                )
            }
            RecordingBar(
                recordState = state.recordState,
                hasChannel = state.currentChannel != null,
                onStart = onStartRecord,
                onStop = onStopRecord
            )
            SearchBar(query = state.searchQuery, onQueryChange = onSearchChange)
            DualFilterDropdowns(
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
    status: PlaybackStatus,
    channelName: String,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit
) {
    val volume by playerEngine.volume.collectAsState()
    val isMuted by playerEngine.isMuted.collectAsState()
    val resizeMode by playerEngine.resizeMode.collectAsState()
    val availableQualities by playerEngine.availableQualities.collectAsState()
    val selectedQuality by playerEngine.selectedQuality.collectAsState()

    var showVolumeSlider by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = playerEngine.exoPlayer
                    useController = true
                    setResizeMode(resizeMode)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.setResizeMode(resizeMode)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Controls (Top & Bottom Bars)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Top Bar: Channel Name & Quick Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (channelName.isNotBlank()) {
                    Text(
                        text = channelName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // زر تبديل جودة البث (SD / HD / Auto)
                    AssistChip(
                        onClick = { showQualityDialog = true },
                        label = {
                            Text(
                                text = selectedQuality?.label ?: "Auto HQ",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AccentTeal
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.HighQuality, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(16.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color.Black.copy(alpha = 0.6f))
                    )

                    // زر تغيير أبعاد الشاشة (Fit / Zoom / Fill)
                    IconButton(
                        onClick = { playerEngine.cycleResizeMode() },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = stringResource(R.string.screen_fit),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // زر ملء الشاشة (Fullscreen)
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = stringResource(R.string.btn_fullscreen),
                            tint = AccentTeal,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Bottom Right Bar: زر وأيقونة الصوت ومستوى الصوت
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // شريط تمرير الصوت (يظهر عند الضغط)
                AnimatedVisibility(visible = showVolumeSlider) {
                    Slider(
                        value = volume,
                        onValueChange = { playerEngine.setVolume(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier
                            .width(100.dp)
                            .padding(horizontal = 4.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = AccentTeal,
                            activeTrackColor = AccentTeal
                        )
                    )
                }

                // أيقونة السماعة (كتم / رفع الصوت)
                IconButton(
                    onClick = { playerEngine.toggleMute() },
                    modifier = Modifier.size(32.dp)
                ) {
                    val volumeIcon = when {
                        isMuted || volume == 0f -> Icons.Default.VolumeMute
                        volume < 0.5f -> Icons.Default.VolumeDown
                        else -> Icons.Default.VolumeUp
                    }
                    Icon(
                        imageVector = volumeIcon,
                        contentDescription = stringResource(R.string.btn_mute),
                        tint = if (isMuted || volume == 0f) LiveRed else AccentTeal,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // سهم صغير لإظهار/إخفاء السلايدر
                IconButton(
                    onClick = { showVolumeSlider = !showVolumeSlider },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (showVolumeSlider) Icons.Default.ChevronRight else Icons.Default.Tune,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Buffering & Error Overlays
        if (status is PlaybackStatus.Buffering) {
            CircularProgressIndicator(color = AccentTeal)
        } else if (status is PlaybackStatus.Error) {
            Text(
                text = stringResource(R.string.error_stream_failed),
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            )
        }
    }

    // نافذة اختيار جودة البث المتاحة
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text(stringResource(R.string.dialog_select_quality), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    // خيار الجودة التلقائية (Adaptive Auto)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                playerEngine.setQuality(null)
                                showQualityDialog = false
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedQuality == null,
                            onClick = {
                                playerEngine.setQuality(null)
                                showQualityDialog = false
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.quality_auto), style = MaterialTheme.typography.bodyLarge)
                    }

                    HorizontalDivider()

                    // الجودات المتوفرة من البث (1080p, 720p, 480p, 360p...)
                    if (availableQualities.isEmpty()) {
                        Text(
                            text = "جاري الكشف عن جودات البث المتعددة...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.padding(12.dp)
                        )
                    } else {
                        availableQualities.forEach { quality ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playerEngine.setQuality(quality)
                                        showQualityDialog = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedQuality == quality,
                                    onClick = {
                                        playerEngine.setQuality(quality)
                                        showQualityDialog = false
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = quality.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (quality.height >= 720) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

/**
 * قائمتان منسدلتان مودرن ومتجاورتان (Dual Dropdown Menus) للتصنيفات والدول
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualFilterDropdowns(
    categories: List<String>,
    selectedCategory: String?,
    countries: List<String>,
    selectedCountry: String?,
    onCategorySelect: (String?) -> Unit,
    onCountrySelect: (String?) -> Unit
) {
    var categoryExpanded by remember { mutableStateOf(false) }
    var countryExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. القائمة المنسدلة للتصنيفات (Categories Dropdown)
        ExposedDropdownMenuBox(
            expanded = categoryExpanded,
            onExpandedChange = { categoryExpanded = !categoryExpanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = selectedCategory ?: stringResource(R.string.all_categories),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.select_category)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                leadingIcon = { Icon(Icons.Default.Category, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(20.dp)) },
                singleLine = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentTeal,
                    unfocusedContainerColor = DarkSurface
                )
            )
            ExposedDropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = { categoryExpanded = false },
                modifier = Modifier.background(DarkSurface)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.all_categories), fontWeight = FontWeight.Bold) },
                    onClick = {
                        onCategorySelect(null)
                        categoryExpanded = false
                    },
                    leadingIcon = { Icon(Icons.Default.DoneAll, contentDescription = null, tint = AccentTeal) }
                )
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category) },
                        onClick = {
                            onCategorySelect(category)
                            categoryExpanded = false
                        },
                        leadingIcon = {
                            if (category == selectedCategory) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = AccentTeal)
                            }
                        }
                    )
                }
            }
        }

        // 2. القائمة المنسدلة للدول مع أعلام الدول (Countries Dropdown with Flag Emojis)
        ExposedDropdownMenuBox(
            expanded = countryExpanded,
            onExpandedChange = { countryExpanded = !countryExpanded },
            modifier = Modifier.weight(1f)
        ) {
            val countryText = if (selectedCountry != null) {
                "${getCountryFlagEmoji(selectedCountry)} $selectedCountry"
            } else {
                stringResource(R.string.all_countries)
            }

            OutlinedTextField(
                value = countryText,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.select_country)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryExpanded) },
                leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(20.dp)) },
                singleLine = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentTeal,
                    unfocusedContainerColor = DarkSurface
                )
            )
            ExposedDropdownMenu(
                expanded = countryExpanded,
                onDismissRequest = { countryExpanded = false },
                modifier = Modifier.background(DarkSurface)
            ) {
                DropdownMenuItem(
                    text = { Text("🌐 ${stringResource(R.string.all_countries)}", fontWeight = FontWeight.Bold) },
                    onClick = {
                        onCountrySelect(null)
                        countryExpanded = false
                    }
                )
                countries.forEach { country ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = getCountryFlagEmoji(country),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(country)
                            }
                        },
                        onClick = {
                            onCountrySelect(country)
                            countryExpanded = false
                        },
                        leadingIcon = {
                            if (country == selectedCountry) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = AccentTeal)
                            }
                        }
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
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
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
                val flagEmoji = getCountryFlagEmoji(channel.countryCode)

                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = channel.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) AccentTeal else Color.White
                            )
                        }
                    },
                    supportingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = flagEmoji,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${channel.groupTitle} ${channel.countryCode?.let { "• $it" } ?: ""}",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
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
                HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
            }
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
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = if (isRecording) LiveRed else Color.Gray,
                    modifier = Modifier.size(16.dp)
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
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
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
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
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
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = DarkSurface
        )
    )
}

@Composable
fun AddPlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var urlText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_playlist), fontWeight = FontWeight.Bold) },
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
