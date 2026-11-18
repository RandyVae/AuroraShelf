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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurorashelf.app.R
import com.aurorashelf.app.cast.CastDevice
import com.aurorashelf.app.cast.CastManager
import com.aurorashelf.app.cast.CastState

/**
 * Apple HIG / Material 3 theme-adaptive bottom sheet for DLNA / UPnP device discovery and cast control.
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
    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        if (!isSearching && devices.isEmpty()) {
            castManager.startDiscovery()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = scheme.surface,
        contentColor = scheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Surface(
                color = scheme.onSurfaceVariant.copy(alpha = 0.35f),
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
                    tint = scheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.cast_sheet_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        ),
                        color = scheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = "请确认电视与手机连接在同一 Wi-Fi 网络",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }

                if (isSearching) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(48.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = scheme.primary,
                        )
                    }
                } else {
                    IconButton(
                        onClick = { castManager.startDiscovery() },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.cast_rescan),
                            tint = scheme.onSurfaceVariant,
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
                    color = scheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, scheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                ) {
                    Text(
                        text = err.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }

            // Device list section title
            Text(
                text = "可投屏设备",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                ),
                color = scheme.onSurfaceVariant,
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
                            tint = scheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(44.dp),
                        )
                        Text(
                            text = if (isSearching) stringResource(R.string.cast_searching_lan) else stringResource(R.string.cast_empty_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                        )
                        if (!isSearching) {
                            Text(
                                text = stringResource(R.string.cast_empty_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
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
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.4f)),
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
                    color = scheme.primary.copy(alpha = 0.14f),
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.CastConnected,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "正在投屏至 ${connected.device.name}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (connected.title.isNotBlank()) {
                        Text(
                            text = connected.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
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
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = if (connected.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (connected.isPlaying) "暂停电视播放" else "播放",
                        tint = scheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(
                    onClick = onDisconnect,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text(
                        text = stringResource(R.string.cast_disconnect),
                        color = scheme.primary,
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
    val scheme = MaterialTheme.colorScheme
    val activeHighlight = isSelected || isPreselected
    Surface(
        color = if (activeHighlight) scheme.primaryContainer.copy(alpha = 0.45f) else scheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (activeHighlight) scheme.primary.copy(alpha = 0.6f) else scheme.outlineVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "${device.name}, ${
                    when {
                        isSelected -> "已连接"
                        isPreselected -> "已选中待播放"
                        else -> "点击投屏"
                    }
                }"
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Surface(
                color = if (activeHighlight) scheme.primary.copy(alpha = 0.16f) else scheme.surface,
                shape = CircleShape,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Tv,
                        contentDescription = null,
                        tint = if (activeHighlight) scheme.primary else scheme.onSurfaceVariant,
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
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = buildString {
                    if (device.ipAddress.isNotBlank()) append(device.ipAddress)
                    if (device.manufacturer.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(device.manufacturer)
                    }
                }.ifBlank { "DLNA 媒体接收器" }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
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
                        color = scheme.primary,
                    )
                }
                isSelected -> {
                    Surface(
                        color = scheme.primary,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = "已连接",
                            color = scheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                isPreselected -> {
                    Surface(
                        color = scheme.primary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.6f)),
                    ) {
                        Text(
                            text = "待投屏",
                            color = scheme.primary,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                else -> {
                    Text(
                        text = "投屏",
                        color = scheme.primary,
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
    val scheme = MaterialTheme.colorScheme
    val mirrorTitle = stringResource(R.string.cast_system_mirror_title)
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = mirrorTitle },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ScreenShare,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = mirrorTitle,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = scheme.onSurface,
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
