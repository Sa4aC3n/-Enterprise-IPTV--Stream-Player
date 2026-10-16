package com.enterprise.iptv.presentation.channels

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.enterprise.iptv.R
import com.enterprise.iptv.domain.model.Channel
import com.enterprise.iptv.domain.recorder.RecordState
import com.enterprise.iptv.presentation.player.PlaybackStatus
import com.enterprise.iptv.presentation.player.PlayerEngine
import com.enterprise.iptv.presentation.player.VideoQuality
import com.enterprise.iptv.presentation.theme.*
import kotlinx.coroutines.delay

/**
 * علامات التبويب الرئيسية في شريط التنقل السفلي المودرن (Netflix / Shahid Style)
 */
enum class AppTab(
    val title: String,
    val unselectedIcon: ImageVector,
    val selectedIcon: ImageVector
) {
    HOME("الرئيسية", Icons.Outlined.Home, Icons.Filled.Home),
    LIVE("البث المباشر", Icons.Outlined.LiveTv, Icons.Filled.LiveTv),
    FAVORITES("المفضلة", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    SEARCH("البحث", Icons.Outlined.Search, Icons.Filled.Search)
}

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
    val context = LocalContext.current
    val activity = context as? Activity
    var currentTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var showAddDialog by remember { mutableStateOf(false) }

    // التحكم في تدوير الشاشة تلقائياً للوضع الأفقي السينمائي عند تكبير الشاشة وإخفاء أشرطة النظام
    DisposableEffect(isFullscreen) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowInsetsControllerCompat(window, window.decorView)
            if (isFullscreen) {
                // الانتقال للوضع الأفقي للسينما
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                // العودة للوضع الرأسي الطبيعي
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.let { act ->
                WindowInsetsControllerCompat(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // الرجوع من وضع ملء الشاشة بزر الرجوع في الهاتف
    BackHandler(enabled = isFullscreen) {
        viewModel.setFullscreen(false)
    }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                ModernTopBar(
                    onAddPlaylistClick = { showAddDialog = true }
                )
            }
        },
        bottomBar = {
            if (!isFullscreen) {
                ModernBottomNavBar(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            }
        },
        containerColor = DarkBackground,
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullscreen) PaddingValues(0.dp) else paddingValues)
                .background(DarkBackground)
        ) {
            when (val state = uiState) {
                is ChannelsUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AccentTeal, strokeWidth = 3.dp)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "جاري تحميل البث والقنوات...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
                is ChannelsUiState.Success -> {
                    CinematicContent(
                        state = state,
                        currentTab = currentTab,
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

/**
 * شريط التطبيق العلوي الزجاجي الفاخر
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernTopBar(
    onAddPlaylistClick: () -> Unit
) {
    Surface(
        color = GlassSurface,
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // شارة لوجو نيونية أنيقة
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(NeonCyan, NeonBlue)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "LIVE STREAMING • ENTERPRISE",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentTeal,
                        letterSpacing = 1.sp
                    )
                }
            }

            IconButton(
                onClick = onAddPlaylistClick,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(DarkCard)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_playlist),
                    tint = AccentTeal,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * شريط التنقل السفلي المودرن الزجاجي (Netflix / Shahid Style)
 */
@Composable
fun ModernBottomNavBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    Surface(
        color = GlassSurface,
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = DarkBorder,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            modifier = Modifier
                .navigationBarsPadding()
                .height(64.dp),
            windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            AppTab.values().forEach { tab ->
                val isSelected = currentTab == tab
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabSelected(tab) },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.title,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = AccentTeal,
                        indicatorColor = AccentTeal,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
            }
        }
    }
}

/**
 * الحاوية التفاعلية السينمائية مع دعم المشغل والشاشات المتقدمة
 */
@Composable
fun CinematicContent(
    state: ChannelsUiState.Success,
    currentTab: AppTab,
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
    val isTabletOrLandscape = configuration.screenWidthDp >= 600

    if (isFullscreen) {
        // وضع ملء الشاشة الكامل
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            VideoPlayerContainer(
                playerEngine = playerEngine,
                status = state.playbackStatus,
                channelName = state.currentChannel?.name ?: "",
                isFullscreen = true,
                onToggleFullscreen = onToggleFullscreen
            )
        }
    } else if (isTabletOrLandscape) {
        // تصميم الأجهزة اللوحية والوضع العرضي (Split Screen)
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
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .background(DarkBackground)
            ) {
                TabContent(
                    state = state,
                    currentTab = currentTab,
                    onChannelSelect = onChannelSelect,
                    onFavoriteToggle = onFavoriteToggle,
                    onCategorySelect = onCategorySelect,
                    onCountrySelect = onCountrySelect,
                    onSearchChange = onSearchChange
                )
            }
        }
    } else {
        // تصميم الهواتف الذكية السينمائي الرأسي
        Column(modifier = Modifier.fillMaxSize()) {
            // المشغل في الجزء العلوي بتصميم زجاجي عائم
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
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

            // شريط التسجيل الحي الصوتي والمرئي
            RecordingBar(
                recordState = state.recordState,
                hasChannel = state.currentChannel != null,
                onStart = onStartRecord,
                onStop = onStopRecord
            )

            // محتوى علامات التبويب (Home, Live, Favorites, Search)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                TabContent(
                    state = state,
                    currentTab = currentTab,
                    onChannelSelect = onChannelSelect,
                    onFavoriteToggle = onFavoriteToggle,
                    onCategorySelect = onCategorySelect,
                    onCountrySelect = onCountrySelect,
                    onSearchChange = onSearchChange
                )
            }
        }
    }
}

/**
 * تبديل المحتوى حسب علامة التبويب المختارة (Home / Live / Favorites / Search)
 */
@Composable
fun TabContent(
    state: ChannelsUiState.Success,
    currentTab: AppTab,
    onChannelSelect: (Channel) -> Unit,
    onFavoriteToggle: (Channel) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onCountrySelect: (String?) -> Unit,
    onSearchChange: (String) -> Unit
) {
    AnimatedContent(
        targetState = currentTab,
        transitionSpec = {
            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
        },
        label = "TabContentAnimation"
    ) { tab ->
        when (tab) {
            AppTab.HOME -> HomeRailsView(
                channels = state.channels,
                currentChannel = state.currentChannel,
                onChannelSelect = onChannelSelect,
                onFavoriteToggle = onFavoriteToggle
            )
            AppTab.LIVE -> LiveTvFilterView(
                state = state,
                onChannelSelect = onChannelSelect,
                onFavoriteToggle = onFavoriteToggle,
                onCategorySelect = onCategorySelect,
                onCountrySelect = onCountrySelect
            )
            AppTab.FAVORITES -> FavoritesView(
                channels = state.channels.filter { it.isFavorite },
                currentChannel = state.currentChannel,
                onChannelSelect = onChannelSelect,
                onFavoriteToggle = onFavoriteToggle
            )
            AppTab.SEARCH -> ModernSearchView(
                query = state.searchQuery,
                channels = state.channels,
                currentChannel = state.currentChannel,
                categories = state.categories,
                onSearchChange = onSearchChange,
                onChannelSelect = onChannelSelect,
                onFavoriteToggle = onFavoriteToggle
            )
        }
    }
}

/**
 * الشاشة الرئيسية بأسلوب Netflix & Shahid (Hero Spotlight + الأشرطة الأفقية Carousels)
 */
@Composable
fun HomeRailsView(
    channels: List<Channel>,
    currentChannel: Channel?,
    onChannelSelect: (Channel) -> Unit,
    onFavoriteToggle: (Channel) -> Unit
) {
    // اختيار القناة البارزة للبانر الرئيسي
    val featuredChannel = currentChannel ?: channels.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. البانر الرئيسي السينمائي (Hero Spotlight)
        if (featuredChannel != null) {
            item {
                HeroSpotlightBanner(
                    channel = featuredChannel,
                    onPlayClick = { onChannelSelect(featuredChannel) },
                    onFavoriteToggle = { onFavoriteToggle(featuredChannel) }
                )
            }
        }

        // 2. شريط القنوات المميزة / الأكثر رواجاً
        val topPicks = channels.take(15)
        if (topPicks.isNotEmpty()) {
            item {
                ChannelCarouselSection(
                    title = "🔥 الأكثر مشاهدة ومختارات البث",
                    channels = topPicks,
                    currentChannel = currentChannel,
                    onChannelSelect = onChannelSelect,
                    onFavoriteToggle = onFavoriteToggle
                )
            }
        }

        // 3. شريط باقة قنوات القرآن الكريم والإسلامية
        val religious = channels.filter { it.groupTitle.contains("إسلام") || it.groupTitle.contains("دين") || it.groupTitle.contains("قرآن") }
        if (religious.isNotEmpty()) {
            item {
                ChannelCarouselSection(
                    title = "🕌 باقة القرآن الكريم والقنوات الإسلامية",
                    channels = religious,
                    currentChannel = currentChannel,
                    onChannelSelect = onChannelSelect,
                    onFavoriteToggle = onFavoriteToggle
                )
            }
        }

        // 4. شريط باقة الرياضة والمباريات
        val sports = channels.filter { it.groupTitle.contains("رياض") }
        if (sports.isNotEmpty()) {
            item {
                ChannelCarouselSection(
                    title = "⚽ القنوات الرياضية والمباريات المباشرة",
                    channels = sports,
                    currentChannel = currentChannel,
                    onChannelSelect = onChannelSelect,
                    onFavoriteToggle = onFavoriteToggle
                )
            }
        }

        // 5. شريط باقة المسلسلات والأفلام
        val dramaAndCinema = channels.filter { it.groupTitle.contains("مسلسل") || it.groupTitle.contains("فيلم") || it.groupTitle.contains("أفلام") || it.groupTitle.contains("دراما") }
        if (dramaAndCinema.isNotEmpty()) {
            item {
                ChannelCarouselSection(
                    title = "🎬 مسلسلات وسينما (Shahid & Netflix)",
                    channels = dramaAndCinema,
                    currentChannel = currentChannel,
                    onChannelSelect = onChannelSelect,
                    onFavoriteToggle = onFavoriteToggle
                )
            }
        }

        // 6. شريط باقة الأخبار والحدث
        val news = channels.filter { it.groupTitle.contains("أخبار") || it.groupTitle.contains("خبر") }
        if (news.isNotEmpty()) {
            item {
                ChannelCarouselSection(
                    title = "📰 قنوات الأخبار والحدث المباشر",
                    channels = news,
                    currentChannel = currentChannel,
                    onChannelSelect = onChannelSelect,
                    onFavoriteToggle = onFavoriteToggle
                )
            }
        }

        // 7. باقة القنوات المصرية الرسمية والإقليمية
        val egyptian = channels.filter { it.countryCode == "EG" }
        if (egyptian.isNotEmpty()) {
            item {
                ChannelCarouselSection(
                    title = "🇪🇬 باقة القنوات المصرية الرسمية والأقاليم",
                    channels = egyptian,
                    currentChannel = currentChannel,
                    onChannelSelect = onChannelSelect,
                    onFavoriteToggle = onFavoriteToggle
                )
            }
        }

        // 8. شريط الوثائقيات والطبيعة
        val docs = channels.filter { it.groupTitle.contains("وثائق") }
        if (docs.isNotEmpty()) {
            item {
                ChannelCarouselSection(
                    title = "🌍 وثائقيات واستكشاف",
                    channels = docs,
                    currentChannel = currentChannel,
                    onChannelSelect = onChannelSelect,
                    onFavoriteToggle = onFavoriteToggle
                )
            }
        }

        // 9. شريط الأطفال والكرتون
        val kids = channels.filter { it.groupTitle.contains("أطفال") || it.groupTitle.contains("كرتون") }
        if (kids.isNotEmpty()) {
            item {
                ChannelCarouselSection(
                    title = "👶 أطفال وكرتون",
                    channels = kids,
                    currentChannel = currentChannel,
                    onChannelSelect = onChannelSelect,
                    onFavoriteToggle = onFavoriteToggle
                )
            }
        }
    }
}

/**
 * البانر الرئيسي السينمائي المتوهج (Hero Spotlight)
 */
@Composable
fun HeroSpotlightBanner(
    channel: Channel,
    onPlayClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            DarkCard,
                            DarkBackground
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // صورة القناة داخل إطار زجاجي فخم
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkElevated)
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(60.dp)
                            .padding(4.dp)
                    )
                }

                Spacer(Modifier.width(16.dp))

                // تفاصيل القناة وأزرار التشغيل
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // مؤشر LIVE النابض
                        LivePulsingBadge()
                        Spacer(Modifier.width(8.dp))
                        // شارة السيرفرات في حال توفرها
                        if (channel.backupUrls.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = AccentTeal.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "${channel.backupUrls.size + 1} سيرفرات",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentTeal,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "${getCountryFlagEmoji(channel.countryCode)}  ${channel.groupTitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // زر تشغيل البث المباشر
                        Button(
                            onClick = onPlayClick,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentTeal,
                                contentColor = Color.Black
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "شاهد الآن",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // زر المفضلة
                        IconButton(
                            onClick = onFavoriteToggle,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkElevated)
                        ) {
                            Icon(
                                imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (channel.isFavorite) LiveRed else Color.LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * شارة LIVE نابضة ومتحركة (Pulsing Live Badge)
 */
@Composable
fun LivePulsingBadge() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = LiveRed.copy(alpha = 0.2f),
        border = androidx.compose.foundation.BorderStroke(1.dp, LiveRed.copy(alpha = alpha))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(LiveRed.copy(alpha = alpha))
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "مباشر LIVE",
                style = MaterialTheme.typography.labelSmall,
                color = LiveRed,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
    }
}

/**
 * شريط أفقي مصنف للقنوات (Horizontal Carousel Section)
 */
@Composable
fun ChannelCarouselSection(
    title: String,
    channels: List<Channel>,
    currentChannel: Channel?,
    onChannelSelect: (Channel) -> Unit,
    onFavoriteToggle: (Channel) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        // عنوان الشريط
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "${channels.size} قناة",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        // قائمة البطاقات الأفقية
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                CinematicChannelCard(
                    channel = channel,
                    isPlaying = channel.id == currentChannel?.id,
                    onClick = { onChannelSelect(channel) },
                    onFavoriteToggle = { onFavoriteToggle(channel) }
                )
            }
        }
    }
}

/**
 * بطاقة القناة السينمائية بتصميم عالمي (16:9 Glassmorphic Poster)
 */
@Composable
fun CinematicChannelCard(
    channel: Channel,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBorder = if (isPlaying) AccentTeal else DarkBorder
    val borderWidth = if (isPlaying) 2.dp else 1.dp

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        modifier = modifier
            .width(150.dp)
            .height(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(borderWidth, cardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // محتوى البطاقة في المنتصف (شعار القناة)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            // الشارات العلوية (علم الدولة على اليمين وزر المفضلة على اليسار)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // علم الدولة
                Text(
                    text = getCountryFlagEmoji(channel.countryCode),
                    style = MaterialTheme.typography.bodyMedium
                )

                // زر المفضلة المصغر
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (channel.isFavorite) LiveRed else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // شريط سفلي متدرج يحتوي على اسم القناة وشارة السيرفرات
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                            color = if (isPlaying) AccentTeal else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (isPlaying) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(AccentTeal)
                            )
                        }
                    }

                    if (channel.backupUrls.isNotEmpty()) {
                        Text(
                            text = "${channel.backupUrls.size + 1} سيرفر",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentTeal,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * شاشة البث المباشر (LIVE TV) مع شبكة القنوات وتصفية التصنيفات والدول
 */
@Composable
fun LiveTvFilterView(
    state: ChannelsUiState.Success,
    onChannelSelect: (Channel) -> Unit,
    onFavoriteToggle: (Channel) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onCountrySelect: (String?) -> Unit
) {
    var isGridView by rememberSaveable { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        // شريط أدوات التصفية المودرن
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "جميع القنوات (${state.channels.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // زر التبديل بين عرض الشبكة والقائمة
            IconButton(
                onClick = { isGridView = !isGridView },
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkCard)
            ) {
                Icon(
                    imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                    contentDescription = null,
                    tint = AccentTeal,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // القائمتان المنسدلتان للتصنيفات والدول
        DualFilterDropdowns(
            categories = state.categories,
            selectedCategory = state.selectedCategory,
            countries = state.countries,
            selectedCountry = state.selectedCountry,
            onCategorySelect = onCategorySelect,
            onCountrySelect = onCountrySelect
        )

        Spacer(Modifier.height(4.dp))

        if (state.channels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_channels_found),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (isGridView) {
            // عرض الشبكة المودرن (2 أعمدة للهواتف)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.channels, key = { it.id }) { channel ->
                    CinematicChannelCard(
                        channel = channel,
                        isPlaying = channel.id == state.currentChannel?.id,
                        onClick = { onChannelSelect(channel) },
                        onFavoriteToggle = { onFavoriteToggle(channel) },
                        modifier = Modifier.fillMaxWidth().height(130.dp)
                    )
                }
            }
        } else {
            // عرض القائمة السلسة
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(state.channels, key = { it.id }) { channel ->
                    ModernListItem(
                        channel = channel,
                        isPlaying = channel.id == state.currentChannel?.id,
                        onClick = { onChannelSelect(channel) },
                        onFavoriteToggle = { onFavoriteToggle(channel) }
                    )
                }
            }
        }
    }
}

/**
 * شاشة المفضلة (FAVORITES)
 */
@Composable
fun FavoritesView(
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "قائمة المفضلة فارغة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "انقر على رمز القلب ❤️ بجوار أي قناة لإضافتها إلى قنواتك المفضلة والوصول إليها بسرعة هنا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(channels, key = { it.id }) { channel ->
                CinematicChannelCard(
                    channel = channel,
                    isPlaying = channel.id == currentChannel?.id,
                    onClick = { onChannelSelect(channel) },
                    onFavoriteToggle = { onFavoriteToggle(channel) },
                    modifier = Modifier.fillMaxWidth().height(130.dp)
                )
            }
        }
    }
}

/**
 * شاشة البحث السريع والذكي (SEARCH)
 */
@Composable
fun ModernSearchView(
    query: String,
    channels: List<Channel>,
    currentChannel: Channel?,
    categories: List<String>,
    onSearchChange: (String) -> Unit,
    onChannelSelect: (Channel) -> Unit,
    onFavoriteToggle: (Channel) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // شريط البحث المودرن
        SearchBar(query = query, onQueryChange = onSearchChange)

        // شرائح التصنيفات السريعة للنقر بنقرة واحدة
        if (categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = query.equals(category, ignoreCase = true),
                        onClick = {
                            if (query.equals(category, ignoreCase = true)) onSearchChange("")
                            else onSearchChange(category)
                        },
                        label = { Text(category, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = DarkCard,
                            selectedContainerColor = AccentTeal,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }
        }

        val filteredList = remember(query, channels) {
            if (query.isBlank()) channels
            else {
                channels.filter {
                    it.name.contains(query, ignoreCase = true) ||
                    it.groupTitle.contains(query, ignoreCase = true) ||
                    (it.countryCode?.contains(query, ignoreCase = true) == true)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "نتائج البحث (${filteredList.size})",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredList, key = { it.id }) { channel ->
                CinematicChannelCard(
                    channel = channel,
                    isPlaying = channel.id == currentChannel?.id,
                    onClick = { onChannelSelect(channel) },
                    onFavoriteToggle = { onFavoriteToggle(channel) },
                    modifier = Modifier.fillMaxWidth().height(130.dp)
                )
            }
        }
    }
}

/**
 * عنصر القائمة المودرن
 */
@Composable
fun ModernListItem(
    channel: Channel,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    val bg = if (isPlaying) AccentTeal.copy(alpha = 0.08f) else Color.Transparent

    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.name,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                    color = if (isPlaying) AccentTeal else Color.White
                )
                if (channel.backupUrls.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = AccentTeal.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "${channel.backupUrls.size + 1} سيرفر",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentTeal,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = getCountryFlagEmoji(channel.countryCode))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${channel.groupTitle} ${channel.countryCode?.let { "• $it" } ?: ""}",
                    color = TextSecondary,
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkCard)
            )
        },
        trailingContent = {
            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (channel.isFavorite) LiveRed else Color.Gray
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = bg),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/**
 * مشغل الفيديو مع لوحة التحكم العائمة الشفافة (Glassmorphic Video Player Container)
 */
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
    val servers by playerEngine.servers.collectAsState()
    val currentServerIndex by playerEngine.currentServerIndex.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // إخفاء أزرار التحكم تلقائياً بعد 4 ثوانٍ أثناء التشغيل لتوفير رؤية سينمائية بدون أي تشويش
    LaunchedEffect(showControls, status) {
        if (showControls && status is PlaybackStatus.Playing) {
            delay(4000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { showControls = !showControls },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = playerEngine.exoPlayer
                    useController = false // إلغاء أزرار إكسوبلاير الافتراضية لمنع التداخل والازدحام نهائياً
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

        // حالة التحميل الدائري النظيف في منتصف الشاشة
        when (status) {
            is PlaybackStatus.Buffering -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    CircularProgressIndicator(color = AccentTeal, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "جاري الاتصال بالبث...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            }
            is PlaybackStatus.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = LiveRed,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.error_stream_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    if (servers.size > 1) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                val nextIndex = (currentServerIndex + 1) % servers.size
                                playerEngine.selectServer(nextIndex)
                            }
                        ) {
                            Text("التبديل لسيرفر بديل", color = AccentTeal)
                        }
                    }
                }
            }
            else -> {
                // زر التشغيل والإيقاف المؤقت المركزي الأنيق عند لمس الشاشة
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    IconButton(
                        onClick = { playerEngine.togglePlayPause() },
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (status is PlaybackStatus.Playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "تشغيل / إيقاف",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }

        // أزرار التحكم العائمة المنسقة بنقاء وأناقة (بدون اسم القناة المزدحم وبدون تداخل الأيقونات)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // الشريط العلوي: شارة مباشر / زر الرجوع + أزرار الخيارات السريعة
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // الجانب الأول: زر الرجوع عند ملء الشاشة أو شارة البث المباشر
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isFullscreen) {
                            IconButton(
                                onClick = onToggleFullscreen,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "خروج من ملء الشاشة",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }

                        // شارة نيونية فاخرة للبث المباشر (LIVE)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(LiveRed)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "مباشر",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // الجانب الثاني: زر الإعدادات الشامل (الجودة + السيرفرات) + أبعاد الشاشة + ملء الشاشة
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. زر الترس: يفتح إعدادات جودة البث والسيرفرات البديلة
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "إعدادات البث والجودة",
                                tint = AccentTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 2. زر أبعاد الشاشة (Fit / Zoom / Stretch)
                        IconButton(
                            onClick = { playerEngine.cycleResizeMode() },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = stringResource(R.string.screen_fit),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 3. زر تكبير الشاشة: يقلب التليفون أوتوماتيكياً بالعرض (Landscape)
                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
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

                // الشريط السفلي: تحكم الصوت في الجانب المقابل بشكل مستقل ومرتب تماماً
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { playerEngine.toggleMute() },
                        modifier = Modifier.size(30.dp)
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
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Slider(
                        value = volume,
                        onValueChange = { playerEngine.setVolume(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier
                            .width(80.dp)
                            .padding(horizontal = 4.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = AccentTeal,
                            activeTrackColor = AccentTeal,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }
    }

    // نافذة إعدادات البث المودرن الشاملة (جودة البث + السيرفرات البديلة + أبعاد العرض)
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("إعدادات المشغل والبث", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    // 1. قسم جودة البث
                    item {
                        Text(
                            text = "جودة البث (Video Quality)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = AccentTeal,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playerEngine.setQuality(null)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedQuality == null,
                                onClick = { playerEngine.setQuality(null) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.quality_auto), style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    items(availableQualities) { quality ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playerEngine.setQuality(quality)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedQuality == quality,
                                onClick = { playerEngine.setQuality(quality) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = quality.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (quality.height >= 720) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    // 2. قسم سيرفرات البث البديلة (إن وجدت)
                    if (servers.size > 1) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = DarkBorder)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "سيرفر البث (Servers)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = AccentTeal,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }

                        items(servers.indices.toList()) { index ->
                            val srv = servers[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playerEngine.selectServer(index)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = currentServerIndex == index,
                                    onClick = { playerEngine.selectServer(index) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(srv.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    // 3. قسم أبعاد الشاشة
                    item {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = DarkBorder)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "أبعاد الشاشة (Aspect Ratio)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = AccentTeal,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        val resizeModes = listOf(
                            AspectRatioFrameLayout.RESIZE_MODE_FIT to "احتواء متناسق (Fit)",
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "ملء الشاشة مع المحافظة على النسبة (Zoom)",
                            AspectRatioFrameLayout.RESIZE_MODE_FILL to "تمديد كامل الشاشة (Stretch)"
                        )

                        resizeModes.forEach { (mode, title) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { playerEngine.setResizeModeDirect(mode) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = resizeMode == mode,
                                    onClick = { playerEngine.setResizeModeDirect(mode) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(title, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("تم", color = AccentTeal, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DarkCard,
            textContentColor = Color.White
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
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. القائمة المنسدلة للتصنيفات
        ExposedDropdownMenuBox(
            expanded = categoryExpanded,
            onExpandedChange = { categoryExpanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = selectedCategory ?: stringResource(R.string.all_categories),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        tint = AccentTeal,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkCard,
                    unfocusedContainerColor = DarkCard,
                    focusedBorderColor = AccentTeal,
                    unfocusedBorderColor = DarkBorder
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = { categoryExpanded = false },
                modifier = Modifier.background(DarkCard)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.all_categories), fontWeight = FontWeight.Bold) },
                    onClick = {
                        onCategorySelect(null)
                        categoryExpanded = false
                    }
                )
                HorizontalDivider()
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = category,
                                color = if (category == selectedCategory) AccentTeal else Color.White,
                                fontWeight = if (category == selectedCategory) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onCategorySelect(category)
                            categoryExpanded = false
                        }
                    )
                }
            }
        }

        // 2. القائمة المنسدلة للدول
        ExposedDropdownMenuBox(
            expanded = countryExpanded,
            onExpandedChange = { countryExpanded = it },
            modifier = Modifier.weight(1f)
        ) {
            val countryDisplayName = if (selectedCountry != null) {
                "${getCountryFlagEmoji(selectedCountry)} $selectedCountry"
            } else {
                "🌐 جميع الدول"
            }

            OutlinedTextField(
                value = countryDisplayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryExpanded) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkCard,
                    unfocusedContainerColor = DarkCard,
                    focusedBorderColor = AccentTeal,
                    unfocusedBorderColor = DarkBorder
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = countryExpanded,
                onDismissRequest = { countryExpanded = false },
                modifier = Modifier.background(DarkCard)
            ) {
                DropdownMenuItem(
                    text = { Text("🌐 جميع الدول", fontWeight = FontWeight.Bold) },
                    onClick = {
                        onCountrySelect(null)
                        countryExpanded = false
                    }
                )
                HorizontalDivider()
                countries.forEach { code ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = getCountryFlagEmoji(code), style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = code,
                                    color = if (code == selectedCountry) AccentTeal else Color.White,
                                    fontWeight = if (code == selectedCountry) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        },
                        onClick = {
                            onCountrySelect(code)
                            countryExpanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * شريط التسجيل المباشر
 */
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
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isRecording) stringResource(R.string.btn_stop_record) else stringResource(R.string.btn_start_record),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

/**
 * شريط البحث المودرن
 */
@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AccentTeal) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.LightGray)
                }
            }
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = DarkCard,
            unfocusedContainerColor = DarkCard,
            focusedBorderColor = AccentTeal,
            unfocusedBorderColor = DarkBorder
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

/**
 * نافذة إضافة رابط قائمة M3U خارجية
 */
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (urlText.isNotBlank()) onConfirm(urlText.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal, contentColor = Color.Black)
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
