package com.enterprise.iptv.presentation.cast

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enterprise.iptv.R
import com.enterprise.iptv.domain.cast.CastDevice
import com.enterprise.iptv.domain.cast.CastDeviceType
import com.enterprise.iptv.domain.cast.CastState
import com.enterprise.iptv.presentation.theme.*

/**
 * نافذة اكتشاف والاتصال بالشاشات الذكية (DLNA / Chromecast / UPnP Smart TVs)
 */
@Composable
fun CastDeviceDialog(
    devices: List<CastDevice>,
    isScanning: Boolean,
    castState: CastState,
    activeDevice: CastDevice?,
    targetMediaTitle: String?,
    onDismiss: () -> Unit,
    onSelectDevice: (CastDevice) -> Unit,
    onTogglePlayPause: () -> Unit,
    onDisconnect: () -> Unit,
    onRescan: () -> Unit
) {
    val isConnected = castState is CastState.Playing || castState is CastState.Paused || castState is CastState.Connected

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) NeonCyan.copy(alpha = 0.2f) else DarkCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.CastConnected else Icons.Default.Cast,
                            contentDescription = null,
                            tint = if (isConnected) NeonCyan else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.cast_title),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (targetMediaTitle != null) {
                            Text(
                                text = targetMediaTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentTeal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = AccentTeal,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onRescan, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "إعادة البحث", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. بطاقة التحكم في البث النشط إذا كانت هناك شاشة متصلة حالياً
                if (activeDevice != null && isConnected) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Tv, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = activeDevice.name,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = when (castState) {
                                                is CastState.Playing -> "▶ جاري البث على التلفاز"
                                                is CastState.Paused -> "⏸ متوقف مؤقتاً"
                                                is CastState.Connecting -> "⏳ جاري الاتصال بالـ TV..."
                                                else -> "متصل بالشاشة"
                                            },
                                            color = if (castState is CastState.Playing) NeonCyan else Color.LightGray,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(36.dp)) {
                                        Icon(
                                            imageVector = if (castState is CastState.Playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "تشغيل / إيقاف مؤقت",
                                            tint = Color.White
                                        )
                                    }
                                    IconButton(onClick = onDisconnect, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "قطع الاتصال", tint = LiveRed)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                Text(
                    text = if (isScanning)
                        stringResource(R.string.cast_searching)
                    else if (devices.isNotEmpty())
                        "اختر الشاشة المراد بث المحتوى إليها:"
                    else
                        stringResource(R.string.cast_no_devices),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(Modifier.height(10.dp))

                if (devices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.TvOff,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (isScanning) "جاري فحص شبكة Wi-Fi المحلية..." else "لم يتم اكتشاف شاشات ذكية متصلة",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                            if (!isScanning) {
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = onRescan,
                                    border = BorderStroke(1.dp, AccentTeal),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("إعادة البحث الآن", color = AccentTeal, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(devices, key = { it.id }) { device ->
                            val isThisDeviceConnected = activeDevice?.id == device.id && isConnected

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isThisDeviceConnected) NeonCyan.copy(alpha = 0.15f) else DarkCard,
                                border = BorderStroke(1.dp, if (isThisDeviceConnected) NeonCyan else DarkBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectDevice(device) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isThisDeviceConnected) NeonCyan.copy(alpha = 0.25f) else DarkBackground),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = when (device.deviceType) {
                                                    CastDeviceType.CHROMECAST -> Icons.Default.Cast
                                                    CastDeviceType.ROKU -> Icons.Default.ConnectedTv
                                                    else -> Icons.Default.Tv
                                                },
                                                contentDescription = null,
                                                tint = if (isThisDeviceConnected) NeonCyan else AccentTeal,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = device.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = if (device.manufacturer.isNotBlank())
                                                    "${device.manufacturer} • ${device.host}"
                                                else
                                                    device.host,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    if (isThisDeviceConnected) {
                                        Text(
                                            text = "متصل",
                                            color = NeonCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PlayCircle,
                                            contentDescription = "بث",
                                            tint = AccentTeal,
                                            modifier = Modifier.size(24.dp)
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
                Text(stringResource(R.string.btn_close), color = AccentTeal, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DarkBackground,
        textContentColor = Color.White
    )
}
