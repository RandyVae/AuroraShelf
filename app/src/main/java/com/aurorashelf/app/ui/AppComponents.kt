@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.animation.ExperimentalSharedTransitionApi::class,
)

package com.aurorashelf.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.graphics.scale
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size as CoilSize
import com.aurorashelf.app.R
import com.aurorashelf.app.model.VideoItem
import com.aurorashelf.app.ui.theme.AuroraBrandAccentDeep
import com.aurorashelf.app.ui.theme.AuroraCoral
import com.aurorashelf.app.ui.theme.AuroraHaptics
import com.aurorashelf.app.ui.theme.AuroraMotion
import com.aurorashelf.app.ui.theme.LocalReduceMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val LocalWindowLayout = compositionLocalOf { WindowLayout.calculate(393f, 690f, false) }
internal val LocalDockHeight = compositionLocalOf { 80.dp }
internal val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
internal val LocalAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

data class CastController(
    val onOpenCastSheet: () -> Unit = {},
    val isCasting: Boolean = false,
)

internal val LocalCastController = compositionLocalOf { CastController() }

/**
 * Material 3 Expressive loading indicator (morphing polygon shapes) with deterministic
 * Reduce Motion fallback to a calm progress indicator.
 */
@Composable
internal fun AuroraLoadingIndicator(
    modifier: Modifier = Modifier,
    contained: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    if (LocalReduceMotion.current) {
        // Respect Android system Remove Animations / Reduced Motion without infinite spin loops.
        CircularProgressIndicator(
            progress = { 0.72f },
            color = color,
            trackColor = color.copy(alpha = 0.22f),
            strokeWidth = 3.dp,
            modifier = modifier.size(32.dp),
        )
    } else if (contained) {
        ContainedLoadingIndicator(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            indicatorColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        LoadingIndicator(
            modifier = modifier,
            color = color,
        )
    }
}

/**
 * Material 3 Expressive wavy progress indicator for top feed refresh and download tasks.
 */
@Composable
internal fun AuroraWavyProgressBar(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val reduceMotion = LocalReduceMotion.current
    if (progress != null) {
        if (reduceMotion) {
            LinearProgressIndicator(
                progress = progress,
                color = color,
                trackColor = trackColor,
                modifier = modifier,
            )
        } else {
            LinearWavyProgressIndicator(
                progress = progress,
                color = color,
                trackColor = trackColor,
                modifier = modifier,
            )
        }
    } else {
        if (reduceMotion) {
            LinearProgressIndicator(
                progress = { 0.55f },
                color = color,
                trackColor = trackColor,
                modifier = modifier,
            )
        } else {
            LinearWavyProgressIndicator(
                color = color,
                trackColor = trackColor,
                modifier = modifier,
            )
        }
    }
}

/**
 * Native Material 3 Expressive PullToRefresh container using `PullToRefreshDefaults.LoadingIndicator`.
 */
@Composable
internal fun AuroraPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorPaddingTop: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val reduceMotion = LocalReduceMotion.current
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
        indicator = {
            if (!reduceMotion) {
                PullToRefreshDefaults.LoadingIndicator(
                    state = state,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = indicatorPaddingTop),
                )
            } else if (isRefreshing) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = indicatorPaddingTop + 12.dp),
                ) {
                    AuroraLoadingIndicator(
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp),
                    )
                }
            }
        },
        content = content,
    )
}

@Composable
internal fun StatusBanner(message: String, onRefresh: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("status-banner"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    AuroraHaptics.tap(haptic)
                    onRefresh()
                },
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "重新加载")
            }
        }
    }
}

@Composable
internal fun VideoListRow(
    video: VideoItem,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Card(
        onClick = {
            AuroraHaptics.tap(haptic)
            onClick()
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = AuroraMotion.defaultSpatial()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(LocalWindowLayout.current.rowThumbnailFraction)
                    .aspectRatio(16 / 10f)
                    .clip(MaterialTheme.shapes.small),
            ) {
                MediaImage(video = video, modifier = Modifier.fillMaxSize())
                if (video.duration.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.74f),
                        shape = RoundedCornerShape(topStart = 8.dp),
                        modifier = Modifier.align(Alignment.BottomEnd),
                    ) {
                        Text(
                            text = video.duration,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (video.author.isNotBlank()) {
                    Text(
                        video.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (video.views.isNotBlank()) {
                    Text(
                        video.views,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = {
                    AuroraHaptics.toggle(haptic, !isFavorite)
                    onFavorite()
                },
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏视频",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val ambientBlurCache = LruCache<String, ImageBitmap>(12)

internal fun createAmbientBlurredBitmap(cacheKey: String?, bytes: ByteArray): ImageBitmap? {
    if (bytes.isEmpty()) return null
    if (!cacheKey.isNullOrBlank()) {
        ambientBlurCache.get(cacheKey)?.let { return it }
    }
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sampleSize = 1
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDim / (sampleSize * 2) >= 48) sampleSize *= 2
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sampled = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
        val targetW = 40
        val targetH = 40
        val tiny = sampled.scale(targetW, targetH)
        if (tiny !== sampled) sampled.recycle()

        val count = targetW * targetH
        val pixels = IntArray(count)
        val scratch = IntArray(count)
        tiny.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        val radius = 3
        val div = radius * 2 + 1
        repeat(2) {
            for (y in 0 until targetH) {
                val rowOffset = y * targetW
                for (x in 0 until targetW) {
                    var rSum = 0
                    var gSum = 0
                    var bSum = 0
                    for (dx in -radius..radius) {
                        val px = pixels[rowOffset + (x + dx).coerceIn(0, targetW - 1)]
                        rSum += (px ushr 16) and 0xFF
                        gSum += (px ushr 8) and 0xFF
                        bSum += px and 0xFF
                    }
                    scratch[rowOffset + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                }
            }
            for (y in 0 until targetH) {
                for (x in 0 until targetW) {
                    var rSum = 0
                    var gSum = 0
                    var bSum = 0
                    for (dy in -radius..radius) {
                        val py = scratch[(y + dy).coerceIn(0, targetH - 1) * targetW + x]
                        rSum += (py ushr 16) and 0xFF
                        gSum += (py ushr 8) and 0xFF
                        bSum += py and 0xFF
                    }
                    pixels[y * targetW + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                }
            }
        }
        tiny.setPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        tiny.asImageBitmap().also { bmp ->
            if (!cacheKey.isNullOrBlank()) ambientBlurCache.put(cacheKey, bmp)
        }
    }.getOrNull()
}

@Composable
internal fun MediaImage(
    video: VideoItem,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    originalSize: Boolean = false,
    ambientBlur: Boolean = false,
    onImageDimensions: ((width: Int, height: Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    var attempt by remember(video.thumbnailUrl, video.pageUrl) { mutableIntStateOf(0) }
    val memoryCachedBytes = remember(video.thumbnailUrl, video.pageUrl, video.title, attempt) {
        if (attempt == 0) {
            video.thumbnailUrl?.let { url ->
                CoverImageLoader.peekMemory(url, video.pageUrl, title = video.title)
            }
        } else {
            null
        }
    }
    var isLoading by remember(video.thumbnailUrl, video.pageUrl, attempt) {
        mutableStateOf(memoryCachedBytes == null && video.thumbnailUrl != null)
    }
    val imageBytes by produceState<ByteArray?>(
        initialValue = memoryCachedBytes,
        key1 = video.thumbnailUrl,
        key2 = video.pageUrl,
        key3 = attempt,
    ) {
        if (memoryCachedBytes != null) {
            isLoading = false
            return@produceState
        }
        value = video.thumbnailUrl?.let { url ->
            withContext(Dispatchers.IO) {
                CoverImageLoader.load(url, video.pageUrl, title = video.title)
            }
        }
        isLoading = false
    }
    val ambientBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = imageBytes,
        key2 = video.thumbnailUrl,
        key3 = ambientBlur,
    ) {
        value = if (ambientBlur && imageBytes != null) {
            withContext(Dispatchers.Default) {
                createAmbientBlurredBitmap(coverMemoryCacheKey(video.thumbnailUrl), imageBytes!!)
            }
        } else {
            null
        }
    }
    val coverRequest = remember(imageBytes, video.thumbnailUrl, originalSize, context) {
        imageBytes?.takeIf { it.isNotEmpty() }?.let { bytes ->
            val key = coverMemoryCacheKey(video.thumbnailUrl)?.let {
                if (originalSize) "$it#original" else it
            }
            ImageRequest.Builder(context)
                .data(bytes)
                .apply {
                    if (originalSize) size(CoilSize.ORIGINAL)
                    if (key != null) memoryCacheKey(key)
                }
                .build()
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (ambientBlur && ambientBitmap != null) {
            Image(
                bitmap = ambientBitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize(),
            )
            DarkModeArtworkScrim()
        } else if (coverRequest != null) {
            AsyncImage(
                model = coverRequest,
                contentDescription = video.title,
                contentScale = contentScale,
                onSuccess = { state ->
                    val s = state.painter.intrinsicSize
                    if (s.width > 0f && s.height > 0f) {
                        onImageDimensions?.invoke(s.width.toInt(), s.height.toInt())
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            DarkModeArtworkScrim()
        } else if (isLoading) {
            AuroraLoadingIndicator(modifier = Modifier.size(28.dp))
        } else {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            if (video.thumbnailUrl != null) {
                IconButton(
                    onClick = { attempt += 1 },
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "重新加载封面",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.DarkModeArtworkScrim() {
    if (MaterialTheme.colorScheme.background.luminance() <= 0.5f) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = DARK_MODE_ARTWORK_SCRIM_ALPHA)),
        )
    }
}

internal fun scaledAdaptiveMinSize(base: Dp, fontScale: Float): Dp =
    if (fontScale < LARGE_FONT_SCALE_THRESHOLD) {
        base
    } else {
        base * fontScale.coerceAtMost(MAX_ARTWORK_FONT_SCALE)
    }

private const val DARK_MODE_ARTWORK_SCRIM_ALPHA = 0.11f
private const val MAX_ARTWORK_FONT_SCALE = 1.6f
private const val LARGE_FONT_SCALE_THRESHOLD = 1.3f

@Composable
internal fun EmptyState(
    message: String,
    icon: ImageVector = Icons.Outlined.FavoriteBorder,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 84.dp, horizontal = 24.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                shape = CircleShape,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

/** Shared sticky category strip used by home, comics, and live feeds. */
@Composable
internal fun CategoryStrip(
    modifier: Modifier = Modifier,
    testTag: String? = null,
    isScrolled: Boolean = false,
    applyBackground: Boolean = true,
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isDark = forceDark || MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val backgroundModifier = if (!applyBackground) {
        Modifier
    } else if (isScrolled) {
        Modifier.background(
            if (isDark) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f)
            else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f),
        )
    } else {
        val edgeColor = if (isDark) Color.Black else MaterialTheme.colorScheme.background
        Modifier.background(
            Brush.verticalGradient(
                listOf(
                    edgeColor.copy(alpha = 0.80f),
                    edgeColor.copy(alpha = 0.36f),
                    Color.Transparent,
                ),
            ),
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(backgroundModifier),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .heightIn(min = 48.dp)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        ) {
            content()
        }
    }
}

/**
 * 100% Native Material 3 Expressive category FilterChip with semantic haptics and full-pill shape.
 */
@Composable
internal fun AuroraFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hasDropdown: Boolean = false,
    forceDark: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = when {
            selected && forceDark -> Color.White
            selected -> scheme.secondaryContainer
            forceDark -> Color.Black.copy(alpha = 0.48f)
            else -> scheme.surfaceContainerHigh
        },
        animationSpec = AuroraMotion.fastEffects(),
        label = "m3-chip-bg",
    )
    val labelColor by animateColorAsState(
        targetValue = when {
            selected && forceDark -> Color.Black
            selected -> scheme.onSecondaryContainer
            forceDark -> Color.White
            else -> scheme.onSurfaceVariant
        },
        animationSpec = AuroraMotion.fastEffects(),
        label = "m3-chip-fg",
    )

    FilterChip(
        selected = selected,
        onClick = {
            AuroraHaptics.segmentTick(haptic)
            onClick()
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        },
        trailingIcon = if (hasDropdown) {
            {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            null
        },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            labelColor = labelColor,
            iconColor = labelColor,
            selectedContainerColor = containerColor,
            selectedLabelColor = labelColor,
            selectedLeadingIconColor = labelColor,
            selectedTrailingIconColor = labelColor,
        ),
        border = if (selected) {
            null
        } else {
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = false,
                borderColor = if (forceDark) Color.White.copy(alpha = 0.28f) else scheme.outlineVariant,
                borderWidth = 1.dp,
            )
        },
        modifier = modifier.heightIn(min = 38.dp),
    )
}

/** Crisp geometric Netgrey brand mark drawn with Material theme primary accent. */
@Composable
internal fun AuroraBrandMark(
    modifier: Modifier = Modifier,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    accentDeepColor: Color = AuroraBrandAccentDeep,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barW = w * 0.30f
        drawRect(
            color = accentDeepColor,
            topLeft = Offset(0f, 0f),
            size = Size(barW, h),
        )
        drawRect(
            color = accentDeepColor,
            topLeft = Offset(w - barW, 0f),
            size = Size(barW, h),
        )
        val diagonalPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(barW, 0f)
            lineTo(w, h)
            lineTo(w - barW, h)
            close()
        }
        drawPath(path = diagonalPath, color = primaryColor)
    }
}

/** Native Material 3 circular tonal profile avatar. */
@Composable
internal fun AuroraProfileAvatar(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 44.dp,
    contentDescription: String = "站点设置",
) {
    val haptic = LocalHapticFeedback.current
    val baseModifier = modifier
        .size(sizeDp)
        .then(
            if (onClick != null) {
                Modifier
                    .minimumInteractiveComponentSize()
                    .semantics { this.contentDescription = contentDescription }
            } else {
                Modifier
            },
        )
    if (onClick != null) {
        Surface(
            onClick = {
                AuroraHaptics.tap(haptic)
                onClick()
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = baseModifier,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(sizeDp * 0.54f),
                )
            }
        }
    } else {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = baseModifier,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(sizeDp * 0.54f),
                )
            }
        }
    }
}

@Composable
internal fun AuroraRankNumber(
    rank: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 34.sp,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
) {
    Text(
        text = "$rank",
        style = MaterialTheme.typography.displaySmall.copy(
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.Black,
        ),
        color = fillColor,
        modifier = modifier,
    )
}

@Composable
internal fun AuroraSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badgeText: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (badgeText != null) {
                    AuroraBadge(text = badgeText)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (actionText != null && onAction != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
internal fun AuroraBadge(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    textColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Surface(
        color = backgroundColor,
        contentColor = textColor,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * Native Material 3 Expressive poster card with standard Ripple indication and tonal elevation.
 */
@Composable
internal fun AuroraPosterCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    ElevatedCard(
        onClick = {
            AuroraHaptics.tap(haptic)
            onClick()
        },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = modifier.width(146.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(206.dp),
            ) {
                MediaImage(
                    video = video,
                    modifier = Modifier.fillMaxSize(),
                )
                if (video.duration.isNotBlank() && video.duration != "--:--") {
                    Surface(
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.74f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                    ) {
                        Text(
                            text = video.duration,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = video.views.ifBlank { video.author }.ifBlank { " " },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Native Material 3 Expressive Top App Bar (clean tonal chrome, 48dp touch targets, Cast action).
 */
@Composable
internal fun AuroraTopBar(
    modifier: Modifier = Modifier,
    titleTestTag: String? = null,
    onCast: (() -> Unit)? = null,
    isCasting: Boolean = LocalCastController.current.isCasting,
    forceDark: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val castController = LocalCastController.current
    val actualOnCast = onCast ?: { castController.onOpenCastSheet() }
    val isDark = forceDark || MaterialTheme.colorScheme.background.luminance() <= 0.5f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        AuroraBrandMark(
            primaryColor = if (forceDark) AuroraCoral else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(width = 20.dp, height = 28.dp),
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = if (forceDark) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .semantics { heading() }
                .then(if (titleTestTag != null) Modifier.testTag(titleTestTag) else Modifier),
        )
        Spacer(Modifier.weight(1f))

        IconButton(
            onClick = {
                AuroraHaptics.tap(haptic)
                actualOnCast()
            },
            modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
            Icon(
                imageVector = if (isCasting) Icons.Filled.CastConnected else Icons.Outlined.Cast,
                contentDescription = if (isCasting) "正在投屏至电视" else "投屏",
                tint = if (isCasting) {
                    MaterialTheme.colorScheme.primary
                } else if (isDark) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }

        actions()
    }
}

/**
 * Native Material 3 pill search trigger in TopBar; collapses to a 48dp IconButton at >= 130% font scale.
 */
@Composable
internal fun AuroraSearchCapsule(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    forceDark: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val fontScale = LocalDensity.current.fontScale
    val isDark = forceDark || MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val containerColor = if (forceDark) {
        Color.Black.copy(alpha = 0.46f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fgColor = if (forceDark) Color.White.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSurfaceVariant

    if (fontScale >= 1.3f) {
        IconButton(
            onClick = {
                AuroraHaptics.tap(haptic)
                onClick()
            },
            modifier = modifier.minimumInteractiveComponentSize(),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = label,
                tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
            )
        }
    } else {
        Surface(
            onClick = {
                AuroraHaptics.tap(haptic)
                onClick()
            },
            shape = CircleShape,
            color = containerColor,
            contentColor = fgColor,
            border = if (forceDark) BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)) else null,
            modifier = modifier
                .heightIn(min = 40.dp)
                .minimumInteractiveComponentSize()
                .semantics { contentDescription = label },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = fgColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = fgColor,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 100% Native Material 3 Expressive SearchBar surface (`surfaceContainerHigh`, full-pill shape,
 * guaranteed 48dp minimum touch targets on every internal icon button).
 */
@Composable
internal fun AuroraSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    onBack: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    isSearching: Boolean = false,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = {
                        AuroraHaptics.tap(haptic)
                        onBack()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = scheme.onSurface,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = if (onBack == null) 16.dp else 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        AuroraHaptics.confirm(haptic)
                        onSearch()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                        .semantics { contentDescription = placeholder },
                )
            }

            if (query.isNotEmpty() && onClear != null) {
                IconButton(
                    onClick = {
                        AuroraHaptics.tap(haptic)
                        onClear()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "清除",
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }

            if (isSearching) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AuroraLoadingIndicator(modifier = Modifier.size(24.dp))
                }
            } else {
                IconButton(
                    onClick = {
                        AuroraHaptics.confirm(haptic)
                        onSearch()
                    },
                    enabled = query.isNotBlank(),
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.action_search),
                        tint = if (query.isNotBlank()) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.45f),
                    )
                }
            }
        }
    }
}

/**
 * 100% Native Android 17 Predictive Back container powered exclusively by [PredictiveBackHandler]
 * and [MaterialTheme.motionScheme]. Removes any custom edge-drag interceptor so system gesture
 * navigation works without gesture contention or competing trajectories.
 */
@Composable
internal fun PredictiveBackContainer(
    enabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var touchDeltaY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val shiftPx = with(density) { 36.dp.toPx() }
    val reduceMotion = LocalReduceMotion.current
    val settleSpec = AuroraMotion.fastSpatial<Float>()

    PredictiveBackHandler(enabled = enabled) { backEvents ->
        var initialY: Float? = null
        try {
            backEvents.collect { event ->
                val startY = initialY ?: event.touchY.also { initialY = it }
                swipeEdge = event.swipeEdge
                touchDeltaY = ((event.touchY - startY) * 0.10f).coerceIn(-shiftPx, shiftPx)
                progress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            onBack()
        } catch (_: CancellationException) {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = settleSpec,
            )
            touchDeltaY = 0f
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress.value
                if (p > 0f) {
                    if (reduceMotion) {
                        alpha = lerp(1f, 0.85f, p)
                    } else {
                        val scale = lerp(1f, 0.90f, p)
                        scaleX = scale
                        scaleY = scale
                        val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                        translationX = direction * shiftPx * p
                        translationY = touchDeltaY * p
                        shape = RoundedCornerShape((28f * p).dp)
                        clip = true
                    }
                }
            },
    ) {
        content()
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
