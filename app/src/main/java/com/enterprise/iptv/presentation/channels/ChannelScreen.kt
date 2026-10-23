package com.enterprise.iptv.presentation.channels

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import java.io.File
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
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
import com.enterprise.iptv.domain.audio.VocalDspMode
import com.enterprise.iptv.domain.caption.SubtitleTrack
import com.enterprise.iptv.domain.cast.CastDevice
import com.enterprise.iptv.domain.cast.CastState
import com.enterprise.iptv.domain.chapter.SmartChapter
import com.enterprise.iptv.domain.meter.NetworkDataStats
import com.enterprise.iptv.domain.model.Channel
import com.enterprise.iptv.domain.model.EpgProgram
import com.enterprise.iptv.domain.recorder.RecordState
import com.enterprise.iptv.domain.voice.VoiceCommand
import com.enterprise.iptv.domain.voice.VoiceRecognitionState
import com.enterprise.iptv.presentation.cast.CastDeviceDialog
import com.enterprise.iptv.presentation.multiview.MultiViewContainer
import com.enterprise.iptv.presentation.multiview.MultiViewLayout
import com.enterprise.iptv.presentation.multiview.MultiViewManager
import com.enterprise.iptv.presentation.player.GestureHudOverlay
import com.enterprise.iptv.presentation.player.GestureHudState
import com.enterprise.iptv.presentation.player.PlaybackStatus
import com.enterprise.iptv.presentation.player.PlayerEngine
import com.enterprise.iptv.presentation.player.VideoQuality
import com.enterprise.iptv.presentation.player.videoPlayerGestures
import com.enterprise.iptv.presentation.theme.*
import kotlinx.coroutines.delay

/**
 * علامات التبويب الرئيسية في شريط التنقل السفلي المودرن مع دعم ثنائي اللغة (عربي / English)
 */
enum class AppTab(
    val titleAr: String,
    val titleEn: String,
    val unselectedIcon: ImageVector,
    val selectedIcon: ImageVector
) {
    HOME("الرئيسية", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    LIVE("البث المباشر", "Live TV", Icons.Outlined.LiveTv, Icons.Filled.LiveTv),
    FAVORITES("المفضلة", "Favorites", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    SEARCH("البحث", "Search", Icons.Outlined.Search, Icons.Filled.Search),
    SETTINGS("الإعدادات", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings);

    fun getTitle(isArabic: Boolean): String = if (isArabic) titleAr else titleEn
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
    modifier: Modifier = Modifier,
    onEnterPip: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val isFullscreen by viewModel.isFullscreen.collectAsState()
    val isInPipMode by viewModel.isInPipMode.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    // في وضع النافذة العائمة (PiP)، يتم عرض المشغل فقط بدون أي أشرطة أو أزرار أو حواف
    if (isInPipMode) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.playerEngine.exoPlayer
                    useController = false
                    setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        return
    }

    val prefs = remember { context.getSharedPreferences("iptv_prefs", Context.MODE_PRIVATE) }
    var currentLanguage by remember { mutableStateOf(prefs.getString("app_lang", "ar") ?: "ar") }
    val isArabic = currentLanguage == "ar"
    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    fun setAppLanguage(lang: String) {
        currentLanguage = lang
        prefs.edit().putString("app_lang", lang).apply()
    }

    fun toggleAppLanguage() {
        val newLang = if (currentLanguage == "ar") "en" else "ar"
        setAppLanguage(newLang)
    }

    var currentTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var showAddDialog by remember { mutableStateOf(false) }
    val voiceState by viewModel.voiceState.collectAsState()
    var showVoiceDialog by remember { mutableStateOf(false) }

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

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Scaffold(
            topBar = {
                if (!isFullscreen) {
                    ModernTopBar(
                        isArabic = isArabic,
                        isKidsMode = (uiState as? ChannelsUiState.Success)?.isKidsMode ?: false,
                        onVoiceClick = {
                            viewModel.startVoiceListening()
                            showVoiceDialog = true
                        },
                        onToggleLanguage = { toggleAppLanguage() },
                        onAddPlaylistClick = { showAddDialog = true }
                    )
                }
            },
            bottomBar = {
                if (!isFullscreen) {
                    ModernBottomNavBar(
                        currentTab = currentTab,
                        isArabic = isArabic,
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
                                    text = if (isArabic) "جاري تحميل البث والقنوات..." else "Loading live streams...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                    is ChannelsUiState.Success -> {
                        val savedRecordings by viewModel.savedRecordings.collectAsState()
                        val sleepTimerSeconds by viewModel.sleepTimerSeconds.collectAsState()
                        val isMultiViewActive by viewModel.isMultiViewActive.collectAsState()

                        // حالة البث للشاشات الذكية (DLNA / Cast)
                        val discoveredCastDevices by viewModel.discoveredCastDevices.collectAsState()
                        val isScanningForCast by viewModel.isScanningForCast.collectAsState()
                        val activeCastDevice by viewModel.activeCastDevice.collectAsState()
                        val castState by viewModel.castState.collectAsState()
                        val networkStats by viewModel.networkStats.collectAsState()
                        var showCastDialog by remember { mutableStateOf(false) }
                        var recordingToCast by remember { mutableStateOf<File?>(null) }

                        CinematicContent(
                            state = state,
                            currentTab = currentTab,
                            isArabic = isArabic,
                            currentLanguage = currentLanguage,
                            onLanguageChange = { setAppLanguage(it) },
                            playerEngine = viewModel.playerEngine,
                            isFullscreen = isFullscreen,
                            onToggleFullscreen = { viewModel.toggleFullscreen() },
                            onChannelSelect = { viewModel.onChannelSelect(it) },
                            onFavoriteToggle = { viewModel.toggleFavorite(it) },
                            onCategorySelect = { viewModel.onCategorySelect(it) },
                            onCountrySelect = { viewModel.onCountrySelect(it) },
                            onSearchChange = { viewModel.onSearchQueryChange(it) },
                            onStartRecord = { viewModel.startRecording() },
                            onStopRecord = { viewModel.stopRecording() },
                            savedRecordings = savedRecordings,
                            onPlayRecording = { viewModel.playRecording(it) },
                            onDeleteRecording = { viewModel.deleteRecording(it) },
                            onEnterPip = onEnterPip,
                            onPlayNext = { viewModel.playNextChannel() },
                            onPlayPrevious = { viewModel.playPreviousChannel() },
                            sleepTimerSeconds = sleepTimerSeconds,
                            onSetSleepTimer = { viewModel.setSleepTimer(it) },
                            isMultiViewActive = isMultiViewActive,
                            multiViewManager = viewModel.multiViewManager,
                            onStartMultiView = { viewModel.startMultiView() },
                            onStopMultiView = { ch -> viewModel.stopMultiView(ch) },
                            onEnableKidsMode = { viewModel.enableKidsMode() },
                            onDisableKidsMode = { pin -> viewModel.disableKidsMode(pin) },
                            onAutoHeal = { viewModel.autoHealCurrentChannel() },
                            onGetChannelSchedule = { ch -> viewModel.getChannelSchedule(ch) },
                            isCasting = castState !is CastState.Disconnected,
                            onOpenCast = {
                                recordingToCast = null
                                viewModel.startCastDiscovery()
                                showCastDialog = true
                            },
                            onCastRecording = { file ->
                                recordingToCast = file
                                viewModel.startCastDiscovery()
                                showCastDialog = true
                            },
                            onOpenVoice = {
                                viewModel.startVoiceListening()
                                showVoiceDialog = true
                            },
                            networkStats = networkStats,
                            onResetDataMeter = { viewModel.resetSessionDataCounter() }
                        )

                        // نافذة اختيار والتحكم في الشاشات الذكية (DLNA / Cast Dialog)
                        if (showCastDialog) {
                            CastDeviceDialog(
                                devices = discoveredCastDevices,
                                isScanning = isScanningForCast,
                                castState = castState,
                                activeDevice = activeCastDevice,
                                targetMediaTitle = recordingToCast?.name ?: state.currentChannel?.name,
                                onDismiss = {
                                    showCastDialog = false
                                    viewModel.stopCastDiscovery()
                                },
                                onSelectDevice = { device ->
                                    if (recordingToCast != null) {
                                        viewModel.castRecordingToDevice(recordingToCast!!, device)
                                    } else {
                                        viewModel.castCurrentChannelToDevice(device)
                                    }
                                },
                                onTogglePlayPause = { viewModel.toggleCastPlayback() },
                                onDisconnect = { viewModel.disconnectCast() },
                                onRescan = { viewModel.startCastDiscovery() }
                            )
                        }

                        // نافذة تأكيد اكتمال وحفظ التسجيل الحي بنجاح
                        if (state.recordState is RecordState.Completed) {
                            val recordedFile = state.recordState.file
                            val sizeMb = "%.2f".format(recordedFile.length() / (1024.0 * 1024.0))
                            AlertDialog(
                                onDismissRequest = { viewModel.dismissRecordCompleted() },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.VideoLibrary,
                                        contentDescription = null,
                                        tint = AccentTeal,
                                        modifier = Modifier.size(36.dp)
                                    )
                                },
                                title = {
                                    Text(
                                        text = if (isArabic) "تم حفظ التسجيل بنجاح!" else "Recording Saved!",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                },
                                text = {
                                    Column {
                                        Text(
                                            text = if (isArabic)
                                                "تم حفظ المقطع المسجل بنجاح في مجلد الفيديوهات الخاص بالتطبيق:"
                                            else
                                                "The recorded stream has been saved successfully in local storage:",
                                            color = Color.LightGray,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            text = "📄 ${recordedFile.name}",
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "💾 الحجم: $sizeMb MB",
                                            color = AccentTeal,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            viewModel.dismissRecordCompleted()
                                            viewModel.playRecording(recordedFile)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentTeal,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (isArabic) "تشغيل التسجيل الآن" else "Play Recording", fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { viewModel.dismissRecordCompleted() }) {
                                        Text(if (isArabic) "إغلاق" else "Close", color = TextSecondary)
                                    }
                                },
                                containerColor = DarkCard
                            )
                        }
                    }
                    is ChannelsUiState.Error -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(state.message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                val importState by viewModel.importState.collectAsState()

                // نافذة الأوامر والبحث الصوتي الذكي (Voice Commands Dialog)
                if (showVoiceDialog) {
                    VoiceCommandDialog(
                        state = voiceState,
                        onDismiss = {
                            showVoiceDialog = false
                            viewModel.stopVoiceListening()
                            viewModel.resetVoiceState()
                        },
                        onRetry = {
                            viewModel.resetVoiceState()
                            viewModel.startVoiceListening()
                        },
                        onExecuteCommand = { cmd ->
                            viewModel.executeVoiceCommand(cmd)
                            if (cmd is VoiceCommand.Search || cmd is VoiceCommand.Unrecognized) {
                                currentTab = AppTab.SEARCH
                            } else if (cmd is VoiceCommand.OpenQualitySettings) {
                                currentTab = AppTab.SETTINGS
                            }
                        }
                    )
                }

                if (showAddDialog) {
                    AddPlaylistDialog(
                        importState = importState,
                        onDismiss = { showAddDialog = false },
                        onConfirmUrl = { url ->
                            viewModel.importM3uPlaylist(url)
                        },
                        onSelectFile = { uri ->
                            viewModel.importM3uFromUri(uri, context.contentResolver)
                        }
                    )
                }

                // نافذة نتيجة استيراد القنوات: نجاح حقيقي مع إحصائية القنوات المستوردة
                if (importState is ImportState.Success) {
                    val count = (importState as ImportState.Success).count
                    AlertDialog(
                        onDismissRequest = {
                            viewModel.dismissImportState()
                            showAddDialog = false
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AccentTeal,
                                modifier = Modifier.size(36.dp)
                            )
                        },
                        title = {
                            Text(
                                text = if (isArabic) "تم استيراد القنوات بنجاح!" else "Import Successful!",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        },
                        text = {
                            Text(
                                text = if (isArabic)
                                    "تم تحليل وإضافة $count قناة جديدة بنجاح إلى مشغل البث وقائمة القنوات."
                                else
                                    "Successfully parsed and added $count new channels to your playlist.",
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.dismissImportState()
                                    showAddDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentTeal,
                                    contentColor = Color.Black
                                )
                            ) {
                                Text(if (isArabic) "رائع" else "Great", fontWeight = FontWeight.Bold)
                            }
                        },
                        containerColor = DarkCard
                    )
                }

                // نافذة نتيجة استيراد القنوات: خطأ حقيقي مع رسالة الخطأ
                if (importState is ImportState.Error) {
                    val errorMsg = (importState as ImportState.Error).message
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissImportState() },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = LiveRed,
                                modifier = Modifier.size(36.dp)
                            )
                        },
                        title = {
                            Text(
                                text = if (isArabic) "تعذر استيراد القائمة" else "Import Failed",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        },
                        text = {
                            Text(
                                text = errorMsg,
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { viewModel.dismissImportState() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentTeal,
                                    contentColor = Color.Black
                                )
                            ) {
                                Text(if (isArabic) "حسناً" else "OK", fontWeight = FontWeight.Bold)
                            }
                        },
                        containerColor = DarkCard
                    )
                }
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
    isArabic: Boolean,
    isKidsMode: Boolean = false,
    onVoiceClick: () -> Unit = {},
    onToggleLanguage: () -> Unit,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        if (isKidsMode) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = AccentTeal.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, AccentTeal)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🧸 فضاء الأطفال", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentTeal)
                                }
                            }
                        }
                    }
                    Text(
                        text = "LIVE STREAMING • ENTERPRISE",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentTeal,
                        letterSpacing = 1.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // زر الأوامر والبحث الصوتي الذكي
                IconButton(
                    onClick = onVoiceClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(DarkCard)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "الأوامر والبحث الصوتي",
                        tint = AccentTeal,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // أيقونة صغيرة وأنيقة لتغيير لغة التطبيق بسرعة
                Surface(
                    onClick = onToggleLanguage,
                    shape = RoundedCornerShape(20.dp),
                    color = DarkCard,
                    border = BorderStroke(1.dp, DarkBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Language",
                            tint = AccentTeal,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isArabic) "عربي" else "EN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
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
}

/**
 * شريط التنقل السفلي المودرن الزجاجي مع دعم الترتيب التلقائي للغات (RTL في العربية و LTR في الإنجليزية)
 */
@Composable
fun ModernBottomNavBar(
    currentTab: AppTab,
    isArabic: Boolean,
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
                val title = tab.getTitle(isArabic)
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabSelected(tab) },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = title,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
    isArabic: Boolean,
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    playerEngine: PlayerEngine,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onChannelSelect: (Channel) -> Unit,
    onFavoriteToggle: (Channel) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onCountrySelect: (String?) -> Unit,
    onSearchChange: (String) -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    savedRecordings: List<File> = emptyList(),
    onPlayRecording: (File) -> Unit = {},
    onDeleteRecording: (File) -> Unit = {},
    onEnterPip: () -> Unit = {},
    onPlayNext: () -> Unit = {},
    onPlayPrevious: () -> Unit = {},
    sleepTimerSeconds: Long? = null,
    onSetSleepTimer: (Int?) -> Unit = {},
    isMultiViewActive: Boolean = false,
    multiViewManager: MultiViewManager? = null,
    onStartMultiView: () -> Unit = {},
    onStopMultiView: (Channel?) -> Unit = {},
    onEnableKidsMode: () -> Unit = {},
    onDisableKidsMode: (String) -> Boolean = { false },
    onAutoHeal: () -> Unit = {},
    onGetChannelSchedule: (Channel) -> List<EpgProgram> = { emptyList() },
    isCasting: Boolean = false,
    onOpenCast: () -> Unit = {},
    onCastRecording: (File) -> Unit = {},
    onOpenVoice: () -> Unit = {},
    networkStats: NetworkDataStats? = null,
    onResetDataMeter: () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val isTabletOrLandscape = configuration.screenWidthDp >= 600
    var showScheduleDialog by remember { mutableStateOf(false) }

    if (isFullscreen) {
        // وضع ملء الشاشة الكامل
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (isMultiViewActive && multiViewManager != null) {
                MultiViewContainer(
                    multiViewManager = multiViewManager,
                    availableChannels = state.channels,
                    onExitMultiView = onStopMultiView
                )
            } else {
                VideoPlayerContainer(
                    playerEngine = playerEngine,
                    status = state.playbackStatus,
                    channelName = state.currentChannel?.name ?: "",
                    isFullscreen = true,
                    onToggleFullscreen = onToggleFullscreen,
                    onEnterPip = onEnterPip,
                    onPlayNext = onPlayNext,
                    onPlayPrevious = onPlayPrevious,
                    sleepTimerSeconds = sleepTimerSeconds,
                    onSetSleepTimer = onSetSleepTimer,
                    onStartMultiView = onStartMultiView,
                    epgNow = state.epgNow,
                    epgNext = state.epgNext,
                    autoHealStatus = state.autoHealStatus,
                    onAutoHeal = onAutoHeal,
                    onOpenSchedule = { showScheduleDialog = true },
                    isCasting = isCasting,
                    onOpenCast = onOpenCast,
                    onOpenVoice = onOpenVoice
                )
            }
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
                if (isMultiViewActive && multiViewManager != null) {
                    MultiViewContainer(
                        multiViewManager = multiViewManager,
                        availableChannels = state.channels,
                        onExitMultiView = onStopMultiView
                    )
                } else {
                    VideoPlayerContainer(
                        playerEngine = playerEngine,
                        status = state.playbackStatus,
                        channelName = state.currentChannel?.name ?: "",
                        isFullscreen = false,
                        onToggleFullscreen = onToggleFullscreen,
                        onEnterPip = onEnterPip,
                        onPlayNext = onPlayNext,
                        onPlayPrevious = onPlayPrevious,
                        sleepTimerSeconds = sleepTimerSeconds,
                        onSetSleepTimer = onSetSleepTimer,
                        onStartMultiView = onStartMultiView,
                        epgNow = state.epgNow,
                        epgNext = state.epgNext,
                        autoHealStatus = state.autoHealStatus,
                        onAutoHeal = onAutoHeal,
                        onOpenSchedule = { showScheduleDialog = true },
                        isCasting = isCasting,
                        onOpenCast = onOpenCast,
                        onOpenVoice = onOpenVoice
                    )
                }
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
                    isArabic = isArabic,
                    currentLanguage = currentLanguage,
                    onLanguageChange = onLanguageChange,
                    onChannelSelect = onChannelSelect,
                    onFavoriteToggle = onFavoriteToggle,
                    onCategorySelect = onCategorySelect,
                    onCountrySelect = onCountrySelect,
                    onSearchChange = onSearchChange,
                    savedRecordings = savedRecordings,
                    onPlayRecording = onPlayRecording,
                    onDeleteRecording = onDeleteRecording,
                    onCastRecording = onCastRecording,
                    isKidsMode = state.isKidsMode,
                    onEnableKidsMode = onEnableKidsMode,
                    onDisableKidsMode = onDisableKidsMode,
                    onVoiceClick = onOpenVoice,
                    networkStats = networkStats,
                    onResetDataMeter = onResetDataMeter
                )
            }
        }
    } else {
        // تصميم الهواتف الذكية السينمائي الرأسي
        Column(modifier = Modifier.fillMaxSize()) {
            // المشغل في الجزء العلوي بتصميم زجاجي عائم
            if (isMultiViewActive && multiViewManager != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Color.Black)
                ) {
                    MultiViewContainer(
                        multiViewManager = multiViewManager,
                        availableChannels = state.channels,
                        onExitMultiView = onStopMultiView
                    )
                }
            } else {
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
                        onToggleFullscreen = onToggleFullscreen,
                        onEnterPip = onEnterPip,
                        onPlayNext = onPlayNext,
                        onPlayPrevious = onPlayPrevious,
                        sleepTimerSeconds = sleepTimerSeconds,
                        onSetSleepTimer = onSetSleepTimer,
                        onStartMultiView = onStartMultiView,
                        epgNow = state.epgNow,
                        epgNext = state.epgNext,
                        autoHealStatus = state.autoHealStatus,
                        onAutoHeal = onAutoHeal,
                        onOpenSchedule = { showScheduleDialog = true },
                        isCasting = isCasting,
                        onOpenCast = onOpenCast,
                        onOpenVoice = onOpenVoice
                    )
                }
            }

            // شريط التسجيل الحي الصوتي والمرئي
            RecordingBar(
                recordState = state.recordState,
                hasChannel = state.currentChannel != null,
                onStart = onStartRecord,
                onStop = onStopRecord
            )

            // محتوى علامات التبويب (Home, Live, Favorites, Search, Settings)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                TabContent(
                    state = state,
                    currentTab = currentTab,
                    isArabic = isArabic,
                    currentLanguage = currentLanguage,
                    onLanguageChange = onLanguageChange,
                    onChannelSelect = onChannelSelect,
                    onFavoriteToggle = onFavoriteToggle,
                    onCategorySelect = onCategorySelect,
                    onCountrySelect = onCountrySelect,
                    onSearchChange = onSearchChange,
                    savedRecordings = savedRecordings,
                    onPlayRecording = onPlayRecording,
                    onDeleteRecording = onDeleteRecording,
                    onCastRecording = onCastRecording,
                    isKidsMode = state.isKidsMode,
                    onEnableKidsMode = onEnableKidsMode,
                    onDisableKidsMode = onDisableKidsMode,
                    onVoiceClick = onOpenVoice,
                    networkStats = networkStats,
                    onResetDataMeter = onResetDataMeter
                )
            }
        }
    }

    if (showScheduleDialog && state.currentChannel != null) {
        ChannelScheduleDialog(
            channel = state.currentChannel,
            schedule = onGetChannelSchedule(state.currentChannel),
            onDismiss = { showScheduleDialog = false }
        )
    }
}

/**
 * تبديل المحتوى حسب علامة التبويب المختارة (Home / Live / Favorites / Search / Settings)
 */
@Composable
fun TabContent(
    state: ChannelsUiState.Success,
    currentTab: AppTab,
    isArabic: Boolean,
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    onChannelSelect: (Channel) -> Unit,
    onFavoriteToggle: (Channel) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onCountrySelect: (String?) -> Unit,
    onSearchChange: (String) -> Unit,
    savedRecordings: List<File> = emptyList(),
    onPlayRecording: (File) -> Unit = {},
    onDeleteRecording: (File) -> Unit = {},
    onCastRecording: (File) -> Unit = {},
    isKidsMode: Boolean = false,
    onEnableKidsMode: () -> Unit = {},
    onDisableKidsMode: (String) -> Boolean = { false },
    onVoiceClick: () -> Unit = {},
    networkStats: NetworkDataStats? = null,
    onResetDataMeter: () -> Unit = {}
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
                onFavoriteToggle = onFavoriteToggle,
                onVoiceClick = onVoiceClick
            )
            AppTab.SETTINGS -> SettingsView(
                isArabic = isArabic,
                currentLanguage = currentLanguage,
                onLanguageChange = onLanguageChange,
                savedRecordings = savedRecordings,
                onPlayRecording = onPlayRecording,
                onDeleteRecording = onDeleteRecording,
                onCastRecording = onCastRecording,
                isKidsMode = isKidsMode,
                onEnableKidsMode = onEnableKidsMode,
                onDisableKidsMode = onDisableKidsMode,
                networkStats = networkStats,
                onResetDataMeter = onResetDataMeter
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
                    title = "🎬 باقة المسلسلات والدراما العربية",
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
    onFavoriteToggle: (Channel) -> Unit,
    onVoiceClick: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // شريط البحث المودرن
        SearchBar(query = query, onQueryChange = onSearchChange, onVoiceClick = onVoiceClick)

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
 * شاشة الإعدادات المودرن الفاخرة (Settings View)
 */
@Composable
fun SettingsView(
    isArabic: Boolean,
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    savedRecordings: List<File> = emptyList(),
    onPlayRecording: (File) -> Unit = {},
    onDeleteRecording: (File) -> Unit = {},
    onCastRecording: (File) -> Unit = {},
    isKidsMode: Boolean = false,
    onEnableKidsMode: () -> Unit = {},
    onDisableKidsMode: (String) -> Boolean = { false },
    networkStats: NetworkDataStats? = null,
    onResetDataMeter: () -> Unit = {}
) {
    val context = LocalContext.current
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showRecordingsDialog by remember { mutableStateOf(false) }
    var showKidsPinDialog by remember { mutableStateOf(false) }

    val packageInfo = remember {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (e: Exception) {
            null
        }
    }
    val appVersionName = packageInfo?.versionName ?: "1.0.2"
    val appVersionCode = packageInfo?.let {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            it.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            it.versionCode.toLong()
        }
    } ?: 3L

    val playStoreAppUrl = "https://play.google.com/store/apps/details?id=com.enterprise.iptv"
    val playStoreDevUrl = "https://play.google.com/store/apps/dev?id=6839550047456072292"

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun shareApp() {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                val text = if (isArabic) {
                    "📺 شاهد أفضل باقات القنوات العربية والعالمية والبث المباشر مجاناً مع تطبيق Enterprise IPTV:\n$playStoreAppUrl"
                } else {
                    "📺 Watch the best live TV channels with Enterprise IPTV app:\n$playStoreAppUrl"
                }
                putExtra(Intent.EXTRA_SUBJECT, "Enterprise IPTV")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(shareIntent, if (isArabic) "مشاركة التطبيق" else "Share App"))
        } catch (e: Exception) {
            // ignore
        }
    }

    fun rateApp() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.enterprise.iptv"))
            context.startActivity(intent)
        } catch (e: Exception) {
            openUrl(playStoreAppUrl)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. بطاقة معلومات التطبيق والإصدار وأيقونة التحديث
        item {
            Surface(
                color = GlassSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(listOf(NeonCyan, NeonBlue))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isArabic) "الإصدار الحالي: $appVersionName" else "Version: $appVersionName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(AccentTeal.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "v$appVersionCode",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentTeal
                                    )
                                }
                            }
                        }
                    }

                    // أيقونة فحص وتحديث التطبيق
                    IconButton(
                        onClick = { showUpdateDialog = true },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(DarkCard)
                            .border(1.dp, DarkBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = if (isArabic) "تحديث التطبيق" else "Update App",
                            tint = AccentTeal,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // 1.1 بطاقة استهلاك البيانات وسرعة الاتصال اللحظية
        if (networkStats != null) {
            item {
                Surface(
                    color = GlassSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AccentTeal.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DataUsage,
                                    contentDescription = null,
                                    tint = AccentTeal,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = if (isArabic) "استهلاك البيانات والباقة" else "Data Usage & Network Speed",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            TextButton(onClick = onResetDataMeter) {
                                Text(if (isArabic) "تصفير العداد" else "Reset", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = if (isArabic) "إجمالي الجلسة" else "Total Session",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = networkStats.formattedSessionData,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Column {
                                Text(
                                    text = if (isArabic) "القناة الحالية" else "Current Channel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = networkStats.formattedCurrentStreamData,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentTeal
                                )
                            }
                            Column {
                                Text(
                                    text = if (isArabic) "سرعة البث اللحظية" else "Live Speed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = networkStats.formattedBitrate,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. بطاقة صفحة المطور وتطبيقاتنا على متجر جوجل
        item {
            Surface(
                color = DarkCard,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AccentTeal.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { openUrl(playStoreDevUrl) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(GoldAmber.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shop,
                                contentDescription = null,
                                tint = GoldAmber,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column {
                            Text(
                                text = if (isArabic) "تطبيقاتنا على متجر جوجل" else "Our Apps on Google Play",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (isArabic) "اضغط هنا لتصفح كافة التطبيقات والإصدارات الأخيرة" else "Browse all our published apps and games",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = AccentTeal,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 3. بطاقة مكتبة التسجيلات المحفوظة محلياً (DVR Recordings Library)
        item {
            Surface(
                color = GlassSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (savedRecordings.isNotEmpty()) AccentTeal.copy(alpha = 0.5f) else DarkBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRecordingsDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(LiveRed.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = LiveRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column {
                            Text(
                                text = if (isArabic) "مكتبة التسجيلات المحفوظة (DVR)" else "Saved Recordings (DVR)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (savedRecordings.isEmpty())
                                    (if (isArabic) "لا توجد تسجيلات بعد • اضغط للاطلاع" else "No recordings yet")
                                else
                                    (if (isArabic) "${savedRecordings.size} تسجيل متوفر • اضغط للتشغيل أو الإدارة" else "${savedRecordings.size} recordings available"),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (savedRecordings.isNotEmpty()) AccentTeal else TextSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = AccentTeal,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 3.1 بطاقة فضاء الأطفال الآمن (Kids Safe Space)
        item {
            Surface(
                color = if (isKidsMode) AccentTeal.copy(alpha = 0.15f) else GlassSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (isKidsMode) AccentTeal else DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentTeal.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChildCare,
                                contentDescription = null,
                                tint = AccentTeal,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isArabic) "فضاء الأطفال الآمن (Kids Space)" else "Kids Safe Space",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("🧸", fontSize = 16.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (isKidsMode)
                                    (if (isArabic) "مفعل • القنوات مقفلة على محتوى الأطفال فقط" else "Active • Kids channels only")
                                else
                                    (if (isArabic) "قفل التطبيق برمز PIN وعرض قنوات الأطفال فقط" else "Lock with PIN to kids content only"),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isKidsMode) AccentTeal else TextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = isKidsMode,
                        onCheckedChange = { checked ->
                            if (checked) {
                                onEnableKidsMode()
                            } else {
                                showKidsPinDialog = true
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = AccentTeal,
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = DarkCard
                        )
                    )
                }
            }
        }

        // 3. بطاقة لغة التطبيق (العربية والانجليزية)
        item {
            Surface(
                color = GlassSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = AccentTeal,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (isArabic) "لغة التطبيق (Language)" else "Application Language",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // خيار اللغة العربية
                        val isArSelected = currentLanguage == "ar"
                        Surface(
                            onClick = { onLanguageChange("ar") },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isArSelected) AccentTeal.copy(alpha = 0.15f) else DarkCard,
                            border = BorderStroke(1.dp, if (isArSelected) AccentTeal else DarkBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🇸🇦", fontSize = 20.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "العربية",
                                            fontWeight = if (isArSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isArSelected) AccentTeal else Color.White,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = if (isArabic) "الافتراضية" else "Default",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                RadioButton(
                                    selected = isArSelected,
                                    onClick = { onLanguageChange("ar") },
                                    colors = RadioButtonDefaults.colors(selectedColor = AccentTeal)
                                )
                            }
                        }

                        // خيار اللغة الانجليزية
                        val isEnSelected = currentLanguage == "en"
                        Surface(
                            onClick = { onLanguageChange("en") },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isEnSelected) AccentTeal.copy(alpha = 0.15f) else DarkCard,
                            border = BorderStroke(1.dp, if (isEnSelected) AccentTeal else DarkBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🇺🇸", fontSize = 20.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "English",
                                            fontWeight = if (isEnSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isEnSelected) AccentTeal else Color.White,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "LTR",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                RadioButton(
                                    selected = isEnSelected,
                                    onClick = { onLanguageChange("en") },
                                    colors = RadioButtonDefaults.colors(selectedColor = AccentTeal)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. بطاقتا مشاركة التطبيق وتقييم التطبيق
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // كارت مشاركة التطبيق
                Surface(
                    color = GlassSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { shareApp() }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(AccentTeal.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = AccentTeal,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (isArabic) "مشاركة التطبيق" else "Share App",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isArabic) "شارك البث مع أصدقائك" else "Share with friends",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // كارت تقييم التطبيق
                Surface(
                    color = GlassSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { rateApp() }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(GoldAmber.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = GoldAmber,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (isArabic) "تقييم التطبيق" else "Rate App",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isArabic) "ادعمنا بـ 5 نجوم" else "Support with 5 stars",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // 5. بطاقات سياسة الخصوصية وشروط الاستخدام
        item {
            Surface(
                color = GlassSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // سياسة الخصوصية
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPrivacyDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = AccentTeal,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = if (isArabic) "سياسة الخصوصية" else "Privacy Policy",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    HorizontalDivider(color = DarkBorder)

                    // شروط الاستخدام
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTermsDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = AccentTeal,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = if (isArabic) "شروط الاستخدام" else "Terms of Service",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    // نافذة تحديث التطبيق Pop-up
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = AccentTeal)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (isArabic) "تحديث التطبيق" else "App Update",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = if (isArabic)
                            "أنت الآن تستخدم أحدث إصدار رقم ($appVersionName)."
                        else
                            "You are currently using the latest version ($appVersionName).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isArabic)
                            "في حال وجود أي إصدار أحدث، يمكنك التحديث تلقائياً وبسهولة عبر متجر Google Play."
                        else
                            "If any newer release is available, you can update automatically via Google Play.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUpdateDialog = false
                        openUrl(playStoreAppUrl)
                    }
                ) {
                    Text(
                        text = if (isArabic) "فحص التحديث على Google Play" else "Check on Google Play",
                        color = AccentTeal,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text(if (isArabic) "إغلاق" else "Close", color = TextSecondary)
                }
            },
            containerColor = DarkCard,
            textContentColor = Color.White
        )
    }

    // نافذة سياسة الخصوصية Pop-up
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = AccentTeal)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (isArabic) "سياسة الخصوصية" else "Privacy Policy",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    item {
                        Text(
                            text = if (isArabic) """
نحن في Enterprise IPTV نقدر خصوصيتك ونلتزم بحمايتها بأعلى المعايير:

1. جمع البيانات:
التطبيق لا يقوم بجمع أو تسجيل أو مشاركة أي بيانات شخصية أو معلومات خاصة بك مع أي طرف ثالث.

2. التخزين المحلي:
يتم حفظ القنوات المفضلة وقوائم التشغيل M3U التي تقوم بإضافتها محلياً فقط على جهازك (Local Storage).

3. أذونات التطبيق:
يستخدم التطبيق إذن الإنترنت فقط لتشغيل البث التلفزيوني المباشر، وإذن التخزين فقط في حال اختيارك حفظ تسجيلات البث (DVR) على جهازك.

4. روابط البث:
التطبيق هو مشغل وسائط تفاعلي، ولا يتحكم في السيرفرات الخارجية أو المحتوى المستضاف عليها.

نلتزم تماماً بسياسات وإرشادات حماية خصوصية المستخدم في Google Play.
                            """.trimIndent()
                            else """
Enterprise IPTV respects your privacy and is committed to protecting your personal data:

1. Data Collection:
This application does not collect, record, or share any personal information or device identifiers with third parties.

2. Local Storage:
Your favorite channels and custom M3U playlists are saved strictly on your local device storage.

3. App Permissions:
Internet permission is used solely to stream live channels. Storage permission is only requested if you choose to record live streams (DVR) to your device.

4. Streaming Content:
The app acts as an advanced IPTV media player and does not host or control third-party streaming content.

Fully compliant with Google Play Developer Privacy Policies.
                            """.trimIndent(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            lineHeight = 20.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text(if (isArabic) "حسناً" else "OK", color = AccentTeal, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DarkCard,
            textContentColor = Color.White
        )
    }

    // نافذة شروط الاستخدام Pop-up
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = AccentTeal)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (isArabic) "شروط الاستخدام" else "Terms of Service",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    item {
                        Text(
                            text = if (isArabic) """
باستخدامك لتطبيق Enterprise IPTV، فإنك توافق على الشروط والأحكام التالية:

1. طبيعة التطبيق:
التطبيق عبارة عن مشغل وسائط متطور (IPTV Player) مخصص لتشغيل البث المفتوح وقوائم التشغيل M3U/M3U8 التي يملك المستخدم حق الوصول إليها.

2. إخلاء المسؤولية:
التطبيق لا يستضيف أي قنوات أو سيرفرات بث على خوادمه الخاصة. المستخدم مسؤول مسؤولية كاملة عن أي روابط أو قوائم تشغيل خارجية يضيفها للتطبيق.

3. الاستخدام العادل:
يحظر استخدام التطبيق بأي طريقة تنتهك حقوق الملكية الفكرية أو القوانين المعمول بها في بلد إقامتك.

4. التحديثات والتوافر:
يسعى فريق العمل لتقديم أفضل جودة واستقرار للمشغل، وقد يتم تحديث الميزات بشكل دوري لتحسين الأداء وتجربة المستخدم.
                            """.trimIndent()
                            else """
By using Enterprise IPTV, you agree to the following terms and conditions:

1. Service Nature:
This application is an IPTV media player designed to play publicly available streams and user-provided M3U/M3U8 playlists.

2. Content Disclaimer:
The app does not host, broadcast, or store media files or channels on proprietary servers. Users are solely responsible for playlists added manually.

3. Fair Use:
Users must adhere to intellectual property rights and applicable local and international copyright laws.

4. Continuous Updates:
We strive to provide the highest playback performance. Features may be updated periodically to enhance stability and user experience.
                            """.trimIndent(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            lineHeight = 20.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTermsDialog = false }) {
                    Text(if (isArabic) "موافق" else "Agree", color = AccentTeal, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DarkCard,
            textContentColor = Color.White
        )
    }

    // نافذة مكتبة التسجيلات المحفوظة (DVR) لإدارتها وتشغيلها وحذفها محلياً
    if (showRecordingsDialog) {
        AlertDialog(
            onDismissRequest = { showRecordingsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = AccentTeal)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isArabic) "مكتبة التسجيلات (${savedRecordings.size})" else "Recordings Library (${savedRecordings.size})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                if (savedRecordings.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (isArabic) "لا توجد مقاطع مسجلة حالياً." else "No recorded streams found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (isArabic)
                                    "اضغط على زر (بدء التسجيل) أثناء مشاهدة أي قناة لتسجيلها وحفظها هنا."
                                else
                                    "Use the REC button while watching any stream to save recordings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(savedRecordings, key = { it.absolutePath }) { file ->
                            val sizeMb = "%.2f MB".format(file.length() / (1024.0 * 1024.0))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = DarkBackground,
                                border = BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = sizeMb,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AccentTeal
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Surface(
                                                color = AccentTeal.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "📑 فصول ذكية",
                                                    color = AccentTeal,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                showRecordingsDialog = false
                                                onPlayRecording(file)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "تشغيل", tint = AccentTeal)
                                        }
                                        IconButton(
                                            onClick = {
                                                showRecordingsDialog = false
                                                onCastRecording(file)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Cast, contentDescription = "بث للشاشة", tint = Color(0xFF4FC3F7))
                                        }
                                        IconButton(
                                            onClick = { onDeleteRecording(file) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = LiveRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRecordingsDialog = false }) {
                    Text(if (isArabic) "إغلاق" else "Close", color = AccentTeal, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DarkCard,
            textContentColor = Color.White
        )
    }

    if (showKidsPinDialog) {
        KidsPinDialog(
            isEnabling = !isKidsMode,
            onDismiss = { showKidsPinDialog = false },
            onConfirmPin = { pin ->
                val success = onDisableKidsMode(pin)
                if (success) {
                    showKidsPinDialog = false
                }
            }
        )
    }
}

/**
 * نافذة الفصول الذكية للتسجيل (Smart Chapters Dialog)
 * تتيح عرض فصول التسجيل والتنقل الفوري بنقرة واحدة إلى أي فصل
 */
@Composable
fun SmartChaptersDialog(
    chapters: List<SmartChapter>,
    currentChapter: SmartChapter?,
    onSeekToChapter: (SmartChapter) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bookmarks,
                    contentDescription = null,
                    tint = AccentTeal,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "📑 فصول التسجيل الذكية",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "إجمالي ${chapters.size} فصول • اضغط للقفز المباشر",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chapters) { chapter ->
                    val isSelected = chapter == currentChapter
                    Surface(
                        onClick = {
                            onSeekToChapter(chapter)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentTeal.copy(alpha = 0.18f) else DarkCard,
                        border = BorderStroke(1.dp, if (isSelected) AccentTeal else DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) AccentTeal else DarkBackground),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "${chapter.index}",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = chapter.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) AccentTeal else Color.White
                                    )
                                    Text(
                                        text = chapter.formattedTimeRange,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Surface(
                                color = DarkBackground,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.5.dp, DarkBorder)
                            ) {
                                Text(
                                    text = chapter.formattedDuration,
                                    color = if (isSelected) AccentTeal else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = AccentTeal)
            }
        },
        containerColor = DarkSurface,
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

/**
 * نافذة إعدادات الترجمة التوضيحية الفورية (Subtitles & Live Captions)
 */
@Composable
fun ClosedCaptionsDialog(
    isCaptionsEnabled: Boolean,
    availableSubtitles: List<SubtitleTrack>,
    selectedSubtitle: SubtitleTrack?,
    isLiveSpeechCaptionsActive: Boolean,
    onToggleCaptions: () -> Unit,
    onSelectSubtitle: (SubtitleTrack?) -> Unit,
    onToggleLiveSpeech: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ClosedCaption,
                    contentDescription = null,
                    tint = AccentTeal,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "💬 الترجمة التوضيحية (Captions)",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "ترجمة مدمجة وتفريغ صوتي لحظي أوفلاين",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // المفتاح العام لتشغيل/إيقاف الترجمة
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AccentTeal.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, AccentTeal.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleCaptions() }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "تفعيل الترجمة التوضيحية",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "عرض نصوص الحوار المباشر أسفل الفيديو",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = isCaptionsEnabled,
                                onCheckedChange = { onToggleCaptions() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AccentTeal,
                                    checkedTrackColor = AccentTeal.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }

                // مسارات الترجمة المدمجة بالبث
                if (availableSubtitles.isNotEmpty()) {
                    item {
                        Text(
                            text = "مسارات الترجمة المدمجة (${availableSubtitles.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentTeal,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    item {
                        Surface(
                            onClick = { onSelectSubtitle(null) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedSubtitle == null && !isLiveSpeechCaptionsActive) AccentTeal.copy(alpha = 0.15f) else DarkCard,
                            border = BorderStroke(1.dp, if (selectedSubtitle == null && !isLiveSpeechCaptionsActive) AccentTeal else DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedSubtitle == null && !isLiveSpeechCaptionsActive,
                                    onClick = { onSelectSubtitle(null) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("إيقاف الترجمة المدمجة", style = MaterialTheme.typography.bodySmall, color = Color.White)
                            }
                        }
                    }

                    items(availableSubtitles) { track ->
                        val isSelected = selectedSubtitle?.id == track.id
                        Surface(
                            onClick = { onSelectSubtitle(track) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) AccentTeal.copy(alpha = 0.18f) else DarkCard,
                            border = BorderStroke(1.dp, if (isSelected) AccentTeal else DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelectSubtitle(track) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(track.displayTitle, style = MaterialTheme.typography.bodySmall, color = if (isSelected) AccentTeal else Color.White)
                            }
                        }
                    }
                }

                // التفريغ الصوتي اللحظي المحلي أوفلاين
                item {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DarkCard,
                        border = BorderStroke(1.dp, if (isLiveSpeechCaptionsActive) NeonCyan else DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleLiveSpeech() }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = if (isLiveSpeechCaptionsActive) NeonCyan else Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "التفريغ الصوتي اللحظي (On-Device STT)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "تفريغ صوتي أوفلاين على معالج الجهاز دون إنترنت",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Switch(
                                checked = isLiveSpeechCaptionsActive,
                                onCheckedChange = { onToggleLiveSpeech() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NeonCyan,
                                    checkedTrackColor = NeonCyan.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("تم", color = AccentTeal, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DarkSurface,
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

/**
 * نافذة اختيار سرعة تشغيل التسجيلات والفيديوهات المحلية (Playback Speed Dialog)
 * خيارات دقيقة من 0.5x إلى 2.0x مع الحفاظ الآلي على نبرة الصوت (Pitch Correction)
 */
@Composable
fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(
        0.5f to "0.5x • بطيء جداً",
        0.75f to "0.75x • بطيء",
        1.0f to "1.0x • السرعة الطبيعية (افتراضي)",
        1.25f to "1.25x • سريع (1.25x)",
        1.5f to "1.5x • سريع جداً (1.5x)",
        2.0f to "2.0x • أقصى سرعة (2.0x)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = AccentTeal,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "⚡ سرعة تشغيل التسجيل",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "مع الحفاظ التلقائي على طبقة ونقاء الصوت",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                speeds.forEach { (speed, label) ->
                    val isSelected = kotlin.math.abs(speed - currentSpeed) < 0.05f
                    Surface(
                        onClick = {
                            onSelectSpeed(speed)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) AccentTeal.copy(alpha = 0.2f) else DarkCard,
                        border = BorderStroke(1.dp, if (isSelected) AccentTeal else DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) NeonCyan else Color.White
                            )
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSelectSpeed(speed)
                                    onDismiss()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AccentTeal,
                                    unselectedColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = AccentTeal, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DarkBackground,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * نافذة الاستماع والتفاعل مع الأوامر الصوتية الذكية (Voice Command Dialog)
 * تصميم نيون مع نبضات ضوئية تفاعلية وعرض حي للنص المسموع
 */
@Composable
fun VoiceCommandDialog(
    state: VoiceRecognitionState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onExecuteCommand: (VoiceCommand) -> Unit
) {
    // إغلاق تلقائي بعد النجاح بثانية ونصف
    LaunchedEffect(state) {
        if (state is VoiceRecognitionState.Success) {
            onExecuteCommand(state.command)
            delay(1500)
            onDismiss()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = AccentTeal,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "الأوامر والبحث الصوتي الذكي",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // أيقونة الميكروفون النابضة
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            when (state) {
                                is VoiceRecognitionState.Success -> AccentTeal.copy(alpha = 0.2f)
                                is VoiceRecognitionState.Error -> LiveRed.copy(alpha = 0.2f)
                                else -> AccentTeal.copy(alpha = 0.2f)
                            }
                        )
                        .border(
                            width = 2.dp,
                            color = when (state) {
                                is VoiceRecognitionState.Success -> AccentTeal
                                is VoiceRecognitionState.Error -> LiveRed
                                else -> NeonCyan
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (state) {
                            is VoiceRecognitionState.Success -> Icons.Default.CheckCircle
                            is VoiceRecognitionState.Error -> Icons.Default.ErrorOutline
                            else -> Icons.Default.Mic
                        },
                        contentDescription = null,
                        tint = when (state) {
                            is VoiceRecognitionState.Success -> AccentTeal
                            is VoiceRecognitionState.Error -> LiveRed
                            else -> AccentTeal
                        },
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // النص الحالي أو النتيجة
                when (state) {
                    VoiceRecognitionState.Idle, VoiceRecognitionState.Listening -> {
                        Text(
                            text = "جاري الاستماع... تحدث الآن",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "أمثلة: «شغل الجزيرة»، «أطفال»، «مؤقت النوم»، «كاريوكي»، «سجل»",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = TextSecondary
                        )
                    }
                    is VoiceRecognitionState.Recognizing -> {
                        Text(
                            text = "«${state.partialText}»",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                    is VoiceRecognitionState.Success -> {
                        Text(
                            text = state.feedbackMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentTeal,
                            textAlign = TextAlign.Center
                        )
                    }
                    is VoiceRecognitionState.Error -> {
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = LiveRed,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentTeal, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("إعادة المحاولة", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        },
        containerColor = DarkSurface,
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

/**
 * نافذة جدول برامج القناة (EPG Schedule Dialog) لليوم بالكامل
 */
@Composable
fun ChannelScheduleDialog(
    channel: Channel?,
    schedule: List<EpgProgram>,
    onDismiss: () -> Unit
) {
    if (channel == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EventNote, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("دليل برامج ${channel.name}", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text("جدول البث لليوم على مدار 24 ساعة", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(schedule, key = { it.id }) { program ->
                    val isAiring = program.isNowPlaying
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isAiring) AccentTeal.copy(alpha = 0.15f) else DarkCard,
                        border = BorderStroke(1.dp, if (isAiring) AccentTeal else DarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = program.title,
                                    fontWeight = if (isAiring) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isAiring) AccentTeal else Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (isAiring) {
                                    Box(
                                        modifier = Modifier
                                            .background(AccentTeal, RoundedCornerShape(12.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("يعرض الآن", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = program.formattedTimeRange,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            if (isAiring) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { program.progressPercent },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = AccentTeal,
                                    trackColor = Color.White.copy(alpha = 0.15f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = AccentTeal, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DarkBackground
    )
}

/**
 * نافذة التحقق من رمز PIN لفضاء الأطفال الآمن (Kids Safe Space PIN)
 */
@Composable
fun KidsPinDialog(
    isEnabling: Boolean,
    onDismiss: () -> Unit,
    onConfirmPin: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Lock, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(32.dp))
        },
        title = {
            Text(
                text = if (isEnabling) "تفعيل فضاء الأطفال الآمن 🧸" else "إلغاء قفل فضاء الأطفال",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isEnabling)
                        "أدخل رمز PIN من 4 أرقام لقفل التطبيق على محتوى الأطفال فقط (الافتراضي: 0000)"
                    else
                        "أدخل رمز PIN المكون من 4 أرقام للخروج من فضاء الأطفال",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 4 && it.all { ch -> ch.isDigit() }) {
                            pin = it
                            error = null
                        }
                    },
                    singleLine = true,
                    placeholder = { Text("••••", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    modifier = Modifier.width(160.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentTeal,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    isError = error != null
                )
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(error!!, color = LiveRed, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length == 4) {
                        onConfirmPin(pin)
                    } else {
                        error = "يجب إدخال 4 أرقام"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal, contentColor = Color.Black)
            ) {
                Text("تأكيد", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        },
        containerColor = DarkCard
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
    onToggleFullscreen: () -> Unit,
    onEnterPip: () -> Unit = {},
    onPlayNext: () -> Unit = {},
    onPlayPrevious: () -> Unit = {},
    sleepTimerSeconds: Long? = null,
    onSetSleepTimer: (Int?) -> Unit = {},
    onStartMultiView: () -> Unit = {},
    epgNow: EpgProgram? = null,
    epgNext: EpgProgram? = null,
    autoHealStatus: String? = null,
    onAutoHeal: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
    isCasting: Boolean = false,
    onOpenCast: () -> Unit = {},
    onOpenVoice: () -> Unit = {}
) {
    val volume by playerEngine.volume.collectAsState()
    val isMuted by playerEngine.isMuted.collectAsState()
    val resizeMode by playerEngine.resizeMode.collectAsState()
    val availableQualities by playerEngine.availableQualities.collectAsState()
    val selectedQuality by playerEngine.selectedQuality.collectAsState()
    val servers by playerEngine.servers.collectAsState()
    val currentServerIndex by playerEngine.currentServerIndex.collectAsState()
    val isAudioOnly by playerEngine.isAudioOnly.collectAsState()
    val dspMode by playerEngine.vocalDspMode.collectAsState()
    val smartChapters by playerEngine.smartChapters.collectAsState()
    val currentChapter by playerEngine.currentChapter.collectAsState()
    val currentPositionMs by playerEngine.currentPositionMs.collectAsState()
    val durationMs by playerEngine.durationMs.collectAsState()
    val isLiveStream by playerEngine.isLiveStream.collectAsState()
    val isCaptionsEnabled by playerEngine.isCaptionsEnabled.collectAsState()
    val currentCaptionText by playerEngine.currentCaptionText.collectAsState()
    val availableSubtitles by playerEngine.availableSubtitles.collectAsState()
    val selectedSubtitle by playerEngine.selectedSubtitle.collectAsState()
    val isLiveSpeechCaptionsActive by playerEngine.isLiveSpeechCaptionsActive.collectAsState()
    val networkStats by playerEngine.networkStats.collectAsState()
    val playbackSpeed by playerEngine.playbackSpeed.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showChaptersDialog by remember { mutableStateOf(false) }
    var showCaptionsDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showOsd by remember { mutableStateOf(false) }
    var dspBadgeMessage by remember { mutableStateOf<String?>(null) }
    var gestureHudState by remember { mutableStateOf<GestureHudState>(GestureHudState.Hidden) }
    val context = LocalContext.current

    // إخفاء إشعار معالج الصوت التلقائي بعد ثانيتين ونصف
    LaunchedEffect(dspBadgeMessage) {
        if (dspBadgeMessage != null) {
            delay(2500)
            dspBadgeMessage = null
        }
    }

    // شريط معلومات القناة (OSD) عند التبديل الخاطف بين القنوات
    LaunchedEffect(channelName) {
        if (channelName.isNotBlank()) {
            showOsd = true
            delay(3000)
            showOsd = false
        }
    }

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
            .videoPlayerGestures(
                context = context,
                isLiveStream = isLiveStream,
                durationMs = durationMs,
                currentPositionMs = currentPositionMs,
                volume = volume,
                onVolumeChange = { playerEngine.setVolume(it) },
                onSeekTo = { playerEngine.seekTo(it) },
                onToggleControls = { showControls = !showControls },
                onHudChange = { gestureHudState = it }
            ),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
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

        // مؤشر التحكم اللمسي والإيماءات في منتصف الشاشة (Gesture HUD)
        GestureHudOverlay(
            state = gestureHudState,
            modifier = Modifier.align(Alignment.Center)
        )

        // شريط عرض الترجمة التوضيحية السينمائي (Subtitle & Live Captions Overlay)
        if (isCaptionsEnabled && currentCaptionText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = if (showControls && !isLiveStream && durationMs > 0) 130.dp else if (showControls) 70.dp else 24.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = currentCaptionText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // واجهة نمط الاستماع الصوتي الموفر للباقة والبطارية
        if (isAudioOnly) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBackground),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(AccentTeal.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = AccentTeal,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = channelName.ifBlank { "بث صوتي مباشر" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "📻 نمط الاستماع الصوتي مفعل (موفر باقة الإنترنت)",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentTeal
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { playerEngine.toggleAudioOnly() },
                        border = BorderStroke(1.dp, AccentTeal),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("العودة لعرض الفيديو", color = AccentTeal, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

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
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = onAutoHeal,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentTeal, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.HealthAndSafety, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("فحص وإصلاح البث تلقائياً", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            else -> {
                // أزرار التحكم المركزية (القناة السابقة • تشغيل/إيقاف • القناة التالية)
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // زر الانتقال للقناة السابقة أو الفصل السابق
                        IconButton(
                            onClick = {
                                if (!isLiveStream && smartChapters.isNotEmpty()) {
                                    playerEngine.seekToPreviousChapter()
                                } else {
                                    onPlayPrevious()
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = if (!isLiveStream && smartChapters.isNotEmpty()) "الفصل السابق" else "القناة السابقة",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // زر التشغيل والإيقاف المركزي
                        IconButton(
                            onClick = { playerEngine.togglePlayPause() },
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                                .border(1.5.dp, AccentTeal, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (status is PlaybackStatus.Playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "تشغيل / إيقاف",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // زر الانتقال للقناة التالية أو الفصل التالي
                        IconButton(
                            onClick = {
                                if (!isLiveStream && smartChapters.isNotEmpty()) {
                                    playerEngine.seekToNextChapter()
                                } else {
                                    onPlayNext()
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = if (!isLiveStream && smartChapters.isNotEmpty()) "الفصل التالي" else "القناة التالية",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
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
                        // 0. زر المشاهدة المتعددة (Multi-View)
                        IconButton(
                            onClick = onStartMultiView,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = "المشاهدة المتعددة (Multi-View)",
                                tint = AccentTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 0.1 زر دليل البرامج (EPG)
                        IconButton(
                            onClick = onOpenSchedule,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventNote,
                                contentDescription = "دليل البرامج (EPG)",
                                tint = AccentTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 1. زر النافذة العائمة الذكية (Picture-in-Picture)
                        IconButton(
                            onClick = onEnterPip,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "النافذة العائمة PiP",
                                tint = AccentTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 1.1 زر البث للشاشات والتلفزيونات الذكية (DLNA / Cast)
                        IconButton(
                            onClick = onOpenCast,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                                contentDescription = "البث للشاشات الذكية (Cast)",
                                tint = if (isCasting) NeonCyan else AccentTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 1.2 معالج الصوت الترددي الفوري (عازل الصوت وموضح الكلام)
                        IconButton(
                            onClick = {
                                val next = playerEngine.cycleVocalDspMode()
                                dspBadgeMessage = when (next) {
                                    VocalDspMode.OFF -> "معالج الصوت: إيقاف (الأصلي)"
                                    VocalDspMode.ENHANCE_VOCALS -> "معالج الصوت: توضيح الكلام (خفت الموسيقى)"
                                    VocalDspMode.REMOVE_VOCALS -> "معالج الصوت: عازل الصوت (كاريوكي / موسيقى فقط)"
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "معالج الصوت الذكي (عزل الصوت / توضيح الكلام)",
                                tint = when (dspMode) {
                                    VocalDspMode.OFF -> AccentTeal
                                    VocalDspMode.ENHANCE_VOCALS -> NeonCyan
                                    VocalDspMode.REMOVE_VOCALS -> LiveRed
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 1.3 زر فصول التسجيل الذكية (Smart Chapters)
                        if (!isLiveStream && smartChapters.isNotEmpty()) {
                            IconButton(
                                onClick = { showChaptersDialog = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmarks,
                                    contentDescription = "فصول التسجيل الذكية",
                                    tint = AccentTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 1.35 زر سرعة تشغيل التسجيلات (Playback Speed)
                        if (!isLiveStream) {
                            Surface(
                                onClick = { showSpeedDialog = true },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.65f),
                                border = BorderStroke(1.dp, if (playbackSpeed != 1.0f) NeonCyan else Color.Transparent),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${playbackSpeed}x".replace(".0x", "x"),
                                        color = if (playbackSpeed != 1.0f) NeonCyan else AccentTeal,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 1.4 زر الترجمة التوضيحية الفورية (Closed Captions)
                        IconButton(
                            onClick = { showCaptionsDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ClosedCaption,
                                contentDescription = "الترجمة التوضيحية (Captions)",
                                tint = if (isCaptionsEnabled) NeonCyan else AccentTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 1.5 زر الأوامر والبحث الصوتي الذكي (Voice Commands)
                        IconButton(
                            onClick = onOpenVoice,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "الأوامر والبحث الصوتي الذكي",
                                tint = AccentTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 1.0 شارة عداد سرعة البث واستهلاك البيانات اللحظي
                        Surface(
                            onClick = { showSettingsDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, DarkBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = networkStats.formattedBitrate,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("•", color = TextSecondary, fontSize = 9.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = networkStats.formattedCurrentStreamData,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentTeal
                                )
                            }
                        }

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

                if (autoHealStatus != null) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .padding(top = 48.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(text = autoHealStatus, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (dspBadgeMessage != null) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AccentTeal.copy(alpha = 0.8f)),
                        modifier = Modifier
                            .padding(top = if (autoHealStatus != null) 80.dp else 48.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(text = dspBadgeMessage ?: "", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // شريط الفصول الذكية والوقت التفاعلي لتسجيلات الفيديو
                if (!isLiveStream && durationMs > 0) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 54.dp, start = 8.dp, end = 8.dp)
                            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmarks,
                                    contentDescription = null,
                                    tint = AccentTeal,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = currentChapter?.title ?: "تسجيل محلي",
                                    color = AccentTeal,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                onClick = { playerEngine.cyclePlaybackSpeed() },
                                shape = RoundedCornerShape(6.dp),
                                color = if (playbackSpeed != 1.0f) AccentTeal.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, if (playbackSpeed != 1.0f) AccentTeal else Color.White.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = "${playbackSpeed}x".replace(".0x", "x"),
                                    color = if (playbackSpeed != 1.0f) NeonCyan else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${SmartChapter.formatTime(currentPositionMs)} / ${SmartChapter.formatTime(durationMs)}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        // شريط التقديم والتأخير التفاعلي مع دعم الفصول
                        Slider(
                            value = currentPositionMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                            onValueChange = { playerEngine.seekTo(it.toLong()) },
                            valueRange = 0f..durationMs.toFloat(),
                            modifier = Modifier.fillMaxWidth().height(20.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = AccentTeal,
                                activeTrackColor = AccentTeal,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            )
                        )

                        // أزرار القفز السريع بين الفصول (السابق / التالي / قائمة الفصول)
                        if (smartChapters.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { playerEngine.seekToPreviousChapter() },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.FastRewind, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("الفصل السابق", color = AccentTeal, fontSize = 10.sp)
                                }

                                TextButton(
                                    onClick = { showChaptersDialog = true },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("📑 جميع الفصول (${smartChapters.size})", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                TextButton(
                                    onClick = { playerEngine.seekToNextChapter() },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("الفصل التالي", color = AccentTeal, fontSize = 10.sp)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.FastForward, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(16.dp))
                                }
                            }
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

        // شريط معلومات القناة العائم (OSD Banner) عند التقليب السريع بين القنوات
        AnimatedVisibility(
            visible = showOsd,
            enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { it / 2 },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, AccentTeal.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(LiveRed)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = channelName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    if (epgNow != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "📺 الآن: ${epgNow.title}",
                            fontSize = 12.sp,
                            color = AccentTeal,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = epgNow.formattedTimeRange,
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                            if (epgNext != null) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "• التالي: ${epgNext.title}",
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { epgNow.progressPercent },
                            modifier = Modifier
                                .width(200.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = AccentTeal,
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                    }
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
                    // نمط الاستماع الصوتي وموفر باقة الإنترنت
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AccentTeal.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, AccentTeal.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { playerEngine.toggleAudioOnly() }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Headphones,
                                        contentDescription = null,
                                        tint = AccentTeal,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "وضع الاستماع الصوتي (Audio Only)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "إيقاف الفيديو لتوفير 80% من باقة الإنترنت",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                Switch(
                                    checked = isAudioOnly,
                                    onCheckedChange = { playerEngine.toggleAudioOnly() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = AccentTeal,
                                        checkedTrackColor = AccentTeal.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = DarkBorder)
                        Spacer(Modifier.height(4.dp))
                    }

                    // قسم معالج الصوت الترددي اللحظي (عازل الصوت وموضح الكلام)
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DarkCard,
                            border = BorderStroke(1.dp, if (dspMode != VocalDspMode.OFF) AccentTeal else DarkBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            tint = if (dspMode != VocalDspMode.OFF) AccentTeal else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "معالج الصوت الذكي (DSP Audio)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Text(
                                        text = when (dspMode) {
                                            VocalDspMode.OFF -> "معطل"
                                            VocalDspMode.ENHANCE_VOCALS -> "توضيح الكلام"
                                            VocalDspMode.REMOVE_VOCALS -> "عازل الصوت"
                                        },
                                        color = when (dspMode) {
                                            VocalDspMode.OFF -> TextSecondary
                                            VocalDspMode.ENHANCE_VOCALS -> NeonCyan
                                            VocalDspMode.REMOVE_VOCALS -> LiveRed
                                        },
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "معالجة فورية بدون إنترنت (Zero Latency) لعزل الصوت الغنائي أو تضخيم المحادثات",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    VocalDspMode.entries.forEach { mode ->
                                        val isCurrent = dspMode == mode
                                        val modeColor = when (mode) {
                                            VocalDspMode.OFF -> AccentTeal
                                            VocalDspMode.ENHANCE_VOCALS -> NeonCyan
                                            VocalDspMode.REMOVE_VOCALS -> LiveRed
                                        }
                                        Surface(
                                            onClick = {
                                                playerEngine.setVocalDspMode(mode)
                                                dspBadgeMessage = "معالج الصوت: ${mode.titleAr}"
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isCurrent) modeColor.copy(alpha = 0.25f) else DarkBackground,
                                            border = BorderStroke(1.dp, if (isCurrent) modeColor else DarkBorder),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = when (mode) {
                                                    VocalDspMode.OFF -> "أصلي"
                                                    VocalDspMode.ENHANCE_VOCALS -> "توضيح صوت"
                                                    VocalDspMode.REMOVE_VOCALS -> "عازل صوت"
                                                },
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCurrent) modeColor else Color.White,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = DarkBorder)
                        Spacer(Modifier.height(4.dp))
                    }

                    // قسم مؤقت النوم التلقائي الذكي (Smart Sleep Timer)
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DarkCard,
                            border = BorderStroke(1.dp, if (sleepTimerSeconds != null) AccentTeal else DarkBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Bedtime,
                                            contentDescription = null,
                                            tint = if (sleepTimerSeconds != null) AccentTeal else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "مؤقت النوم التلقائي (Sleep Timer)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    if (sleepTimerSeconds != null) {
                                        val min = sleepTimerSeconds / 60
                                        val sec = sleepTimerSeconds % 60
                                        Text(
                                            text = "%02d:%02d".format(min, sec),
                                            color = AccentTeal,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(15, 30, 60, 90).forEach { mins ->
                                        val isCurrent = sleepTimerSeconds != null && (sleepTimerSeconds + 59) / 60 == mins.toLong()
                                        Surface(
                                            onClick = { onSetSleepTimer(mins) },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isCurrent) AccentTeal else DarkBackground,
                                            border = BorderStroke(1.dp, if (isCurrent) AccentTeal else DarkBorder),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = "$mins د",
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCurrent) Color.Black else Color.White,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                    }
                                    if (sleepTimerSeconds != null) {
                                        Surface(
                                            onClick = { onSetSleepTimer(null) },
                                            shape = RoundedCornerShape(8.dp),
                                            color = LiveRed.copy(alpha = 0.2f),
                                            border = BorderStroke(1.dp, LiveRed),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = "إلغاء",
                                                textAlign = TextAlign.Center,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = LiveRed,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = DarkBorder)
                        Spacer(Modifier.height(4.dp))
                    }

                    // 0. بطاقة عداد استهلاك البيانات وسرعة الاتصال اللحظية
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DarkBackground,
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = null,
                                            tint = NeonCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "مؤشر السرعة واستهلاك البيانات",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    TextButton(
                                        onClick = { playerEngine.resetSessionDataCounter() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("تصفير", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // سرعة البث اللحظية
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("سرعة التدفق", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Text(
                                            text = networkStats.formattedBitrate,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = NeonCyan
                                        )
                                    }
                                    // استهلاك القناة الحالية
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("القناة الحالية", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Text(
                                            text = networkStats.formattedCurrentStreamData,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentTeal
                                        )
                                    }
                                    // إجمالي استهلاك الجلسة
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("إجمالي الجلسة", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Text(
                                            text = networkStats.formattedSessionData,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

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

                    // 4. قسم سرعة التشغيل للتسجيلات (إن لم يكن بثاً مباشراً)
                    if (!isLiveStream) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = DarkBorder)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "سرعة تشغيل الفيديو (Playback Speed)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = AccentTeal,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )

                            val speedsList = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                speedsList.forEach { sp ->
                                    val isSel = kotlin.math.abs(sp - playbackSpeed) < 0.05f
                                    Surface(
                                        onClick = { playerEngine.setPlaybackSpeed(sp) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSel) AccentTeal else DarkCard,
                                        border = BorderStroke(1.dp, if (isSel) AccentTeal else DarkBorder),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${sp}x".replace(".0x", "x"),
                                                color = if (isSel) Color.Black else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
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

    // نافذة الفصول الذكية للتسجيلات (Smart Chapters Dialog)
    if (showChaptersDialog && smartChapters.isNotEmpty()) {
        SmartChaptersDialog(
            chapters = smartChapters,
            currentChapter = currentChapter,
            onSeekToChapter = { playerEngine.seekToChapter(it) },
            onDismiss = { showChaptersDialog = false }
        )
    }

    // نافذة إعدادات الترجمة التوضيحية الفورية (Closed Captions Dialog)
    if (showCaptionsDialog) {
        ClosedCaptionsDialog(
            isCaptionsEnabled = isCaptionsEnabled,
            availableSubtitles = availableSubtitles,
            selectedSubtitle = selectedSubtitle,
            isLiveSpeechCaptionsActive = isLiveSpeechCaptionsActive,
            onToggleCaptions = { playerEngine.toggleCaptions() },
            onSelectSubtitle = { playerEngine.selectSubtitleTrack(it) },
            onToggleLiveSpeech = { playerEngine.toggleLiveSpeechCaptions() },
            onDismiss = { showCaptionsDialog = false }
        )
    }

    // نافذة سرعة التشغيل للتسجيلات والفيديوهات المحلية (Playback Speed Dialog)
    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            currentSpeed = playbackSpeed,
            onSelectSpeed = { playerEngine.setPlaybackSpeed(it) },
            onDismiss = { showSpeedDialog = false }
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
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onVoiceClick: () -> Unit = {}
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AccentTeal) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.LightGray)
                    }
                }
                IconButton(onClick = onVoiceClick) {
                    Icon(Icons.Default.Mic, contentDescription = "الأوامر والبحث الصوتي", tint = AccentTeal)
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
 * نافذة إضافة قنوات M3U خارجية (عبر رابط ويب مباشر أو عبر اختيار ملف من ذاكرة الجهاز)
 */
@Composable
fun AddPlaylistDialog(
    importState: ImportState,
    onDismiss: () -> Unit,
    onConfirmUrl: (String) -> Unit,
    onSelectFile: (Uri) -> Unit
) {
    var urlText by remember { mutableStateOf("") }
    val isLoading = importState is ImportState.Loading

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onSelectFile(it) }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = AccentTeal)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_playlist), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "يمكنك إضافة باقة قنواتك الخاصة عبر اختيار ملف M3U من الهاتف أو لصق رابط مباشر:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(Modifier.height(14.dp))

                // 1. زر اختيار ملف من ذاكرة الهاتف
                OutlinedButton(
                    onClick = {
                        filePickerLauncher.launch(arrayOf("*/*", "text/*", "audio/x-mpegurl", "application/vnd.apple.mpegurl"))
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AccentTeal),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("📁 اختيار ملف M3U من الهاتف", color = AccentTeal, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = DarkBorder)
                    Text(" أو لصق رابط ويب ", style = MaterialTheme.typography.labelSmall, color = TextMuted, modifier = Modifier.padding(horizontal = 8.dp))
                    HorizontalDivider(modifier = Modifier.weight(1f), color = DarkBorder)
                }

                Spacer(Modifier.height(14.dp))

                // 2. حقل إدخال الرابط
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    enabled = !isLoading,
                    placeholder = { Text(stringResource(R.string.playlist_url_hint), style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkCard,
                        unfocusedContainerColor = DarkCard,
                        focusedBorderColor = AccentTeal,
                        unfocusedBorderColor = DarkBorder
                    )
                )

                if (isLoading) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = AccentTeal, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("جاري قراءة واستيراد القنوات...", style = MaterialTheme.typography.bodySmall, color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (urlText.isNotBlank()) onConfirmUrl(urlText.trim())
                },
                enabled = !isLoading && urlText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal, contentColor = Color.Black)
            ) {
                Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.btn_cancel), color = TextSecondary)
            }
        },
        containerColor = DarkCard,
        textContentColor = Color.White
    )
}
