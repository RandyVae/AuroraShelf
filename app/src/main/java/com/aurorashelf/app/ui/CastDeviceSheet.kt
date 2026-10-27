package com.aurorashelf.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurorashelf.app.cast.CastDevice
import com.aurorashelf.app.cast.CastManager
import com.aurorashelf.app.cast.CastState
import com.aurorashelf.app.ui.theme.AuroraCoral

/**
 * Apple HIG / Netflix styled bottom sheet for DLNA / UPnP device discovery and cast control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CastDeviceSheet(
    castManager: CastManager,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onDeviceSelected: ((CastDevice) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val devices by castManager.devices.collectAsStateWithLifecycle()
    val isSearching by castManager.isSearching.collectAsStateWithLifecycle()
    val castState by castManager.castState.collectAsStateWithLifecycle()
    val preselectedDevice by castManager.selectedDevice.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (!isSearching && devices.isEmpty()) {
            castManager.startDiscovery()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF14171F),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                color = Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(3.dp),
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp),
            ) {}
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Cast,
                    contentDescription = null,
                    tint = AuroraCoral,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "投屏至電視",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        ),
                        color = Color.White,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "請確保電視與手機在同一個 Wi-Fi 網絡",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }

                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = AuroraCoral,
                    )
                } else {
                    IconButton(
                        onClick = { castManager.startDiscovery() },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重新搜尋設備",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Active Casting Card (if connected)
            if (castState is CastState.Connected) {
                val connected = castState as CastState.Connected
                ActiveCastCard(
                    connected = connected,
                    onPlay = { castManager.play() },
                    onPause = { castManager.pause() },
                    onDisconnect = { castManager.disconnect() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                )
            } else if (castState is CastState.Error) {
                val err = castState as CastState.Error
                Surface(
                    color = Color(0xFF2B1922),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AuroraCoral.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                ) {
                    Text(
                        text = err.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }

            // Device list section title
            Text(
                text = "可投屏設備",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                ),
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 6.dp),
            )

            // Device list or Empty State
            if (devices.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tv,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(44.dp),
                        )
                        Text(
                            text = if (isSearching) "正在搜尋局域網內的電視設備..." else "未搜尋到可投屏設備",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                        if (!isSearching) {
                            Text(
                                text = "請檢查電視是否已開機，且開啟 DLNA/投屏功能",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.45f),
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    items(devices, key = { it.id }) { device ->
                        val isCurrentDevice = (castState as? CastState.Connected)?.device?.id == device.id
                        val isConnectingThis = (castState as? CastState.Connecting)?.device?.id == device.id
                        val isPreselected = !isCurrentDevice && preselectedDevice?.id == device.id

                        CastDeviceItem(
                            device = device,
                            isSelected = isCurrentDevice,
                            isPreselected = isPreselected,
                            isConnecting = isConnectingThis,
                            onClick = {
                                if (onDeviceSelected != null) {
                                    onDeviceSelected(device)
                                } else {
                                    castManager.selectDevice(device)
                                    onDismissRequest()
                                }
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // System Screen Mirroring Fallback
            SystemCastFallbackButton(
                onClick = {
                    openSystemCastSettings(context)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun ActiveCastCard(
    connected: CastState.Connected,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color(0xFF1E2330),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AuroraCoral.copy(alpha = 0.4f)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    color = AuroraCoral.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.CastConnected,
                            contentDescription = null,
                            tint = AuroraCoral,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "正在投屏至 ${connected.device.name}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (connected.title.isNotBlank()) {
                        Text(
                            text = connected.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    onClick = { if (connected.isPlaying) onPause() else onPlay() },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (connected.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (connected.isPlaying) "暫停電視播放" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(
                    onClick = onDisconnect,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = "斷開投屏",
                        color = AuroraCoral,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

@Composable
private fun CastDeviceItem(
    device: CastDevice,
    isSelected: Boolean,
    isPreselected: Boolean = false,
    isConnecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeHighlight = isSelected || isPreselected
    Surface(
        color = if (activeHighlight) Color(0xFF222838) else Color(0xFF1B1E28),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (activeHighlight) AuroraCoral.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "${device.name}, ${
                    when {
                        isSelected -> "已連接"
                        isPreselected -> "已選中待播放"
                        else -> "點擊投屏"
                    }
                }"
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Surface(
                color = if (activeHighlight) AuroraCoral.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f),
                shape = CircleShape,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Tv,
                        contentDescription = null,
                        tint = if (activeHighlight) AuroraCoral else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (activeHighlight) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = buildString {
                    if (device.ipAddress.isNotBlank()) append(device.ipAddress)
                    if (device.manufacturer.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(device.manufacturer)
                    }
                }.ifBlank { "DLNA 媒體接收器" }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            when {
                isConnecting -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = AuroraCoral,
                    )
                }
                isSelected -> {
                    Surface(
                        color = AuroraCoral,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = "已連接",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                isPreselected -> {
                    Surface(
                        color = AuroraCoral.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AuroraCoral.copy(alpha = 0.6f)),
                    ) {
                        Text(
                            text = "待投屏",
                            color = AuroraCoral,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                else -> {
                    Text(
                        text = "投屏",
                        color = AuroraCoral,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemCastFallbackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "系統無線投屏與螢幕鏡像" },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ScreenShare,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "系統無線投屏 / 螢幕鏡像",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

private fun openSystemCastSettings(context: Context) {
    val castIntent = Intent("android.settings.CAST_SETTINGS").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(castIntent)
    }.onFailure {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
