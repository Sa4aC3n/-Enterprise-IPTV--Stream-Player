package com.enterprise.iptv.presentation.multiview

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.enterprise.iptv.domain.model.Channel
import com.enterprise.iptv.presentation.player.PlaybackStatus
import com.enterprise.iptv.presentation.theme.*

@OptIn(UnstableApi::class)
@Composable
fun MultiViewContainer(
    multiViewManager: MultiViewManager,
    availableChannels: List<Channel>,
    onExitMultiView: (Channel?) -> Unit,
    modifier: Modifier = Modifier
) {
    val layout by multiViewManager.layout.collectAsState()
    val slots by multiViewManager.slots.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    var activePickerSlotId by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // الشريط العلوي للتحكم في وضع البث المتعدد
            MultiViewTopBar(
                layout = layout,
                onToggleLayout = {
                    val next = if (layout == MultiViewLayout.DUAL) MultiViewLayout.QUAD else MultiViewLayout.DUAL
                    multiViewManager.setLayout(next, availableChannels)
                },
                onExit = {
                    val targetChannel = multiViewManager.exit()
                    onExitMultiView(targetChannel)
                }
            )

            // شبكة الشاشات (2 شاشة أو 4 شاشات)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                if (layout == MultiViewLayout.DUAL) {
                    if (isLandscape) {
                        // شاشتان أفقياً (جنباً إلى جنب في الوضع العرضي)
                        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            slots.take(2).forEach { slot ->
                                MultiViewQuadrant(
                                    slot = slot,
                                    multiViewManager = multiViewManager,
                                    onFocus = { multiViewManager.focusSlot(slot.slotId) },
                                    onChangeChannel = { activePickerSlotId = slot.slotId },
                                    onMaximize = {
                                        val ch = slot.channel ?: multiViewManager.exit()
                                        multiViewManager.exit()
                                        onExitMultiView(ch)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    } else {
                        // شاشتان رأسياً (فوق بعض في الوضع الرأسي)
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            slots.take(2).forEach { slot ->
                                MultiViewQuadrant(
                                    slot = slot,
                                    multiViewManager = multiViewManager,
                                    onFocus = { multiViewManager.focusSlot(slot.slotId) },
                                    onChangeChannel = { activePickerSlotId = slot.slotId },
                                    onMaximize = {
                                        val ch = slot.channel ?: multiViewManager.exit()
                                        multiViewManager.exit()
                                        onExitMultiView(ch)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                )
                            }
                        }
                    }
                } else {
                    // 4 شاشات (شبكة 2x2 متساوية)
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            slots.filter { it.slotId in 0..1 }.forEach { slot ->
                                MultiViewQuadrant(
                                    slot = slot,
                                    multiViewManager = multiViewManager,
                                    onFocus = { multiViewManager.focusSlot(slot.slotId) },
                                    onChangeChannel = { activePickerSlotId = slot.slotId },
                                    onMaximize = {
                                        val ch = slot.channel ?: multiViewManager.exit()
                                        multiViewManager.exit()
                                        onExitMultiView(ch)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            slots.filter { it.slotId in 2..3 }.forEach { slot ->
                                MultiViewQuadrant(
                                    slot = slot,
                                    multiViewManager = multiViewManager,
                                    onFocus = { multiViewManager.focusSlot(slot.slotId) },
                                    onChangeChannel = { activePickerSlotId = slot.slotId },
                                    onMaximize = {
                                        val ch = slot.channel ?: multiViewManager.exit()
                                        multiViewManager.exit()
                                        onExitMultiView(ch)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            }
        }

        // نافذة اختيار القناة للشاشة المحددة
        if (activePickerSlotId != null) {
            ChannelPickerDialog(
                channels = availableChannels,
                onDismiss = { activePickerSlotId = null },
                onSelectChannel = { chosen ->
                    activePickerSlotId?.let { slotId ->
                        multiViewManager.setSlotChannel(slotId, chosen)
                    }
                    activePickerSlotId = null
                }
            )
        }
    }
}

@Composable
fun MultiViewTopBar(
    layout: MultiViewLayout,
    onToggleLayout: () -> Unit,
    onExit: () -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.85f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onExit,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = null,
                            tint = AccentTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "المشاهدة المتعددة (Multi-View)",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Text(
                        text = if (layout == MultiViewLayout.DUAL) "شاشتان متزامنتان" else "4 شاشات متزامنة",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // زر التبديل بين شاشتين و 4 شاشات
            Button(
                onClick = onToggleLayout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentTeal.copy(alpha = 0.2f),
                    contentColor = AccentTeal
                ),
                border = BorderStroke(1.dp, AccentTeal),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = if (layout == MultiViewLayout.DUAL) Icons.Default.Dashboard else Icons.Default.ViewAgenda,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (layout == MultiViewLayout.DUAL) "التحويل إلى 4 شاشات" else "التحويل إلى شاشتين",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun MultiViewQuadrant(
    slot: MultiViewSlotState,
    multiViewManager: MultiViewManager,
    onFocus: () -> Unit,
    onChangeChannel: () -> Unit,
    onMaximize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (slot.isFocused) NeonCyan else Color.White.copy(alpha = 0.15f)
    val borderWidth = if (slot.isFocused) 2.dp else 1.dp
    val player = multiViewManager.getPlayer(slot.slotId)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .clickable { onFocus() }
    ) {
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // حالة التحميل أثناء البث
        if (slot.status is PlaybackStatus.Buffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = AccentTeal,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else if (slot.status is PlaybackStatus.Error) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = LiveRed, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("تعذر البث", color = Color.White, fontSize = 11.sp)
                    TextButton(onClick = onChangeChannel) {
                        Text("تغيير القناة", color = AccentTeal, fontSize = 11.sp)
                    }
                }
            }
        }

        // الشريط العلوي داخل الربع
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // شارة اسم القناة والصوت
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = if (slot.isFocused) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = null,
                    tint = if (slot.isFocused) NeonCyan else TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = slot.channel?.name ?: "شاشة ${slot.slotId + 1}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // أزرار التحكم في الشاشة: تغيير القناة + تكبير
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onChangeChannel,
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "تبديل القناة",
                        tint = AccentTeal,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onMaximize,
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "تكبير",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelPickerDialog(
    channels: List<Channel>,
    onDismiss: () -> Unit,
    onSelectChannel: (Channel) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(searchQuery, channels) {
        if (searchQuery.isBlank()) channels
        else channels.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            (it.category?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "اختر قناة لهذه الشاشة",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ابحث عن اسم القناة...", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentTeal,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = AccentTeal)
                    }
                )

                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.id }) { ch ->
                        Surface(
                            onClick = { onSelectChannel(ch) },
                            color = DarkCard,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!ch.logoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ch.logoUrl,
                                        contentDescription = ch.name,
                                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(4.dp))
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ch.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!ch.category.isNullOrBlank()) {
                                        Text(
                                            text = ch.category,
                                            color = TextSecondary,
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
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = AccentTeal)
            }
        },
        containerColor = DarkBackground
    )
}
