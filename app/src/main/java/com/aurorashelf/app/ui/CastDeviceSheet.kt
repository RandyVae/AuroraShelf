@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.aurorashelf.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurorashelf.app.R
import com.aurorashelf.app.cast.CastDevice
import com.aurorashelf.app.cast.CastManager
import com.aurorashelf.app.cast.CastState
import com.aurorashelf.app.cast.LocalNetworkPermission
import com.aurorashelf.app.ui.theme.AuroraHaptics

/**
 * 100% Native Material 3 Expressive bottom sheet for DLNA / UPnP device discovery and cast control.
 * Supports Android 17 (API 37) `ACCESS_LOCAL_NETWORK` runtime permission gating.
 */
@Composable
internal fun CastDeviceSheet(
    castManager: CastManager,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onDeviceSelected: ((CastDevice) -> Unit)? = null,
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.PartiallyExpanded))
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val devices by castManager.devices.collectAsStateWithLifecycle()
    val isSearching by castManager.isSearching.collectAsStateWithLifecycle()
    val castState by castManager.castState.collectAsStateWithLifecycle()
    val preselectedDevice by castManager.selectedDevice.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    var hasLocalNetworkPermission by remember {
        mutableStateOf(LocalNetworkPermission.isGranted(context))
    }
    var permissionDeniedOnce by remember { mutableStateOf(false) }

    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasLocalNetworkPermission = granted || LocalNetworkPermission.isGranted(context)
        if (hasLocalNetworkPermission) {
            permissionDeniedOnce = false
            AuroraHaptics.confirm(haptic)
            castManager.startDiscovery()
        } else {
            permissionDeniedOnce = true
            AuroraHaptics.reject(haptic)
        }
    }

    LaunchedEffect(hasLocalNetworkPermission) {
        if (hasLocalNetworkPermission && !isSearching && devices.isEmpty()) {
            castManager.startDiscovery()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = scheme.surfaceContainerLow,
        contentColor = scheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
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
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.cast_system_mirror_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }

                if (isSearching) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(48.dp),
                    ) {
                        AuroraLoadingIndicator(modifier = Modifier.size(26.dp))
                    }
                } else {
                    IconButton(
                        onClick = {
                            AuroraHaptics.tap(haptic)
                            if (hasLocalNetworkPermission) {
                                castManager.startDiscovery()
                            } else {
                                localNetworkPermissionLauncher.launch(LocalNetworkPermission.PERMISSION)
                            }
                        },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.cast_rescan),
                            tint = scheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (castState is CastState.Connected) {
                val connected = castState as CastState.Connected
                ActiveCastCard(
                    connected = connected,
                    onPlay = {
                        AuroraHaptics.tap(haptic)
                        castManager.play()
                    },
                    onPause = {
                        AuroraHaptics.tap(haptic)
                        castManager.pause()
                    },
                    onDisconnect = {
                        AuroraHaptics.confirm(haptic)
                        castManager.disconnect()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                )
            } else if (castState is CastState.Error) {
                val err = castState as CastState.Error
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = scheme.errorContainer,
                        contentColor = scheme.onErrorContainer,
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                ) {
                    Text(
                        text = err.message,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }

            if (!hasLocalNetworkPermission) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = scheme.secondaryContainer,
                        contentColor = scheme.onSecondaryContainer,
                    ),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Default.Wifi, contentDescription = null)
                            Text(
                                text = stringResource(R.string.cast_local_network_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            text = stringResource(R.string.cast_local_network_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Button(
                                onClick = {
                                    AuroraHaptics.tap(haptic)
                                    localNetworkPermissionLauncher.launch(LocalNetworkPermission.PERMISSION)
                                },
                                shape = CircleShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.cast_local_network_grant))
                            }
                            if (permissionDeniedOnce) {
                                OutlinedButton(
                                    onClick = { openAppSettings(context) },
                                    shape = CircleShape,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text(stringResource(R.string.cast_local_network_settings))
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "可投屏设备",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )

                if (devices.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (isSearching) {
                                AuroraLoadingIndicator(contained = true)
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Tv,
                                    contentDescription = null,
                                    tint = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(44.dp),
                                )
                            }
                            Text(
                                text = if (isSearching) stringResource(R.string.cast_searching_lan) else stringResource(R.string.cast_empty_title),
                                style = MaterialTheme.typography.bodyLarge,
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
                                    AuroraHaptics.confirm(haptic)
                                    if (onDeviceSelected != null) {
                                        onDeviceSelected(device)
                                    } else {
                                        castManager.selectDevice(device)
                                        onDismissRequest()
                                    }
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            SystemCastFallbackButton(
                onClick = {
                    AuroraHaptics.tap(haptic)
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
    Card(
        colors = CardDefaults.cardColors(
            containerColor = scheme.primaryContainer.copy(alpha = 0.45f),
            contentColor = scheme.onSurface,
        ),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.35f)),
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
                    color = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.CastConnected,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "正在投屏至 ${connected.device.name}",
                        style = MaterialTheme.typography.titleSmall,
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
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(
                    onClick = onDisconnect,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text(
                        text = stringResource(R.string.cast_disconnect),
                        color = scheme.primary,
                        style = MaterialTheme.typography.labelLarge,
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
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (activeHighlight) scheme.secondaryContainer else scheme.surfaceContainerHigh,
            contentColor = if (activeHighlight) scheme.onSecondaryContainer else scheme.onSurface,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
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
                color = if (activeHighlight) scheme.primaryContainer else scheme.surfaceContainer,
                contentColor = if (activeHighlight) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Tv,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (activeHighlight) FontWeight.Bold else FontWeight.SemiBold,
                    ),
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
                    AuroraLoadingIndicator(modifier = Modifier.size(22.dp))
                }
                isSelected -> {
                    AuroraBadge(
                        text = "已连接",
                        backgroundColor = scheme.primary,
                        textColor = scheme.onPrimary,
                    )
                }
                isPreselected -> {
                    AuroraBadge(
                        text = "待投屏",
                        backgroundColor = scheme.primaryContainer,
                        textColor = scheme.onPrimaryContainer,
                    )
                }
                else -> {
                    Text(
                        text = "投屏",
                        color = scheme.primary,
                        style = MaterialTheme.typography.labelLarge,
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
    val mirrorTitle = stringResource(R.string.cast_system_mirror_title)
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = mirrorTitle },
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ScreenShare,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = mirrorTitle,
            style = MaterialTheme.typography.labelLarge,
        )
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
            context.startActivity(
                Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
