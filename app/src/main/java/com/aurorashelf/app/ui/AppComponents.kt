@file:OptIn(
    dev.chrisbanes.haze.ExperimentalHazeApi::class,
    dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi::class,
)

package com.aurorashelf.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.aurorashelf.app.model.AppDestination
import com.aurorashelf.app.model.ContentSource
import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.ComicSourceInfo
import com.aurorashelf.app.model.FeedCategory
import com.aurorashelf.app.model.VideoItem
import com.aurorashelf.app.data.SourceAddress
import com.aurorashelf.app.data.PlaybackEpisode
import com.aurorashelf.app.data.PlaybackResolver
import com.aurorashelf.app.data.OfflineCacheManager
import com.aurorashelf.app.data.OfflineCacheState
import com.aurorashelf.app.data.OfflineVideo
import com.aurorashelf.app.ui.liquid.LiquidBottomNavigation
import com.aurorashelf.app.ui.liquid.LiquidNavigationTab
import com.aurorashelf.app.ui.theme.AuroraCoral
import com.aurorashelf.app.ui.theme.AuroraBrandAccentDeep
import com.aurorashelf.app.ui.theme.AuroraDimens
import com.aurorashelf.app.ui.theme.AuroraGlassContainerDark
import com.aurorashelf.app.ui.theme.AuroraGlassContainerLight
import com.aurorashelf.app.ui.theme.AuroraImmersiveBackground
import com.aurorashelf.app.ui.theme.AuroraInkContent
import com.aurorashelf.app.ui.theme.AuroraInkSurface
import com.aurorashelf.app.ui.theme.AuroraMediaInk
import com.aurorashelf.app.ui.theme.AuroraMediaLabel
import com.aurorashelf.app.ui.theme.AuroraMediaMeta
import com.aurorashelf.app.ui.theme.AuroraMediaMuted
import com.aurorashelf.app.ui.theme.AuroraMediaOnImage
import com.aurorashelf.app.ui.theme.AuroraMediaOutline
import com.aurorashelf.app.ui.theme.AuroraMediaPlaceholder
import com.aurorashelf.app.ui.theme.AuroraMediaRankFill
import com.aurorashelf.app.ui.theme.AuroraMediaRankOutline
import com.aurorashelf.app.ui.theme.AuroraMediaSurface
import com.aurorashelf.app.ui.theme.AuroraMediaSurfaceVariant
import com.aurorashelf.app.ui.theme.AuroraProfileAccent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

internal val LocalWindowLayout = staticCompositionLocalOf { WindowLayout.calculate(393f, 690f, false) }
internal val LocalDockHeight = staticCompositionLocalOf { 112.dp }
internal val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }
internal val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

data class CastController(
    val onOpenCastSheet: () -> Unit = {},
    val isCasting: Boolean = false,
)

/**
 * Apple HIG fluid spring physics specification tokens.
 * Mimics iOS system-wide continuous fluid interruptible motion.
 */
object AppleSpring {
    /**
     * Crisp, micro-bouncy spring for buttons, chips, card presses and toggles.
     * Mimics iOS UIControl tactile spring (damping ratio 0.82, stiffness 380).
     */
    val Snappy = spring<Float>(
        dampingRatio = 0.82f,
        stiffness = 380f,
    )

    val SnappyDp = spring<Dp>(
        dampingRatio = 0.82f,
        stiffness = 380f,
    )

    /**
     * Smooth, non-oscillating fluid spring for tab switches, sheets, and morph expansions.
     * Mimics iOS UIViewController transitions (damping ratio 0.88, stiffness 260).
     */
    val Gentle = spring<Float>(
        dampingRatio = 0.88f,
        stiffness = 260f,
    )

    val GentleOffset = spring<IntOffset>(
        dampingRatio = 0.88f,
        stiffness = 260f,
    )

    /**
     * Playful rubber-band spring for dock indicators and pull-to-refresh releases.
     * Mimics iOS UIScrollView rubber-band bounce (damping ratio 0.72, stiffness 200).
     */
    val Bouncy = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = 200f,
    )
}

/**
 * Apple HIG fluid press scale micro-interaction.
 * Depresses the target element to [pressedScale] (default 0.96f) on finger-down,
 * and releases with Apple's signature low-bouncy spring physics on finger-up.
 */
fun Modifier.applePressEffect(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    onClick: (() -> Unit)? = null,
): Modifier = composed {
    if (!enabled) return@composed this
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = AppleSpring.Snappy,
        label = "applePressScale",
    )
    val haptic = LocalHapticFeedback.current

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            } else {
                Modifier
            }
        )
}

internal val LocalCastController = staticCompositionLocalOf { CastController() }

@Composable
internal fun StatusBanner(message: String, onRefresh: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "重新加载")
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
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(LocalWindowLayout.current.rowThumbnailFraction)
                    .aspectRatio(16 / 10f)
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                MediaImage(video = video, modifier = Modifier.fillMaxSize())
                Text(
                    text = video.duration,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(topStart = 10.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                )
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
                Text(video.author, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(video.views, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏视频",
                    tint = if (isFavorite) AuroraCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun MediaImage(video: VideoItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var attempt by remember(video.thumbnailUrl, video.pageUrl) { mutableIntStateOf(0) }
    var isLoading by remember(video.thumbnailUrl, video.pageUrl, attempt) { mutableStateOf(true) }
    val imageBytes by produceState<ByteArray?>(
        initialValue = null,
        key1 = video.thumbnailUrl,
        key2 = video.pageUrl,
        key3 = attempt,
    ) {
        value = video.thumbnailUrl?.let { url ->
            withContext(Dispatchers.IO) { CoverImageLoader.load(url, video.pageUrl) }
        }
        isLoading = false
    }
    // A ByteArray model has no stable cache key on its own; using the URL as the memory key lets
    // Coil reuse the decoded bitmap when the row scrolls back instead of decoding it again.
    // Empty payloads never produce a request, so they cannot reach the decoder.
    val coverRequest = remember(imageBytes, video.thumbnailUrl, context) {
        imageBytes?.takeIf { it.isNotEmpty() }?.let { bytes ->
            val key = coverMemoryCacheKey(video.thumbnailUrl)
            ImageRequest.Builder(context)
                .data(bytes)
                .apply { if (key != null) memoryCacheKey(key) }
                .build()
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (coverRequest != null) {
            AsyncImage(
                model = coverRequest,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            DarkModeArtworkScrim()
        } else if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            // Covers are best-effort: show a neutral placeholder plus an explicit retry
            // affordance instead of failing silently (HIG: keep the user in control).
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

/**
 * HIG A7: network artwork is dimmed slightly in dark mode so covers do not glare at night.
 * The scrim only paints; it has no pointer input, so taps keep reaching the parent card.
 */
@Composable
internal fun BoxScope.DarkModeArtworkScrim() {
    if (isSystemInDarkTheme()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = DARK_MODE_ARTWORK_SCRIM_ALPHA)),
        )
    }
}

/**
 * C3/C7: scale the adaptive artwork column width with the system font scale (capped at 1.6x)
 * so a large font naturally collapses the grid to a single column instead of squeezing cards.
 * Below the large-text threshold the base width is kept untouched, so default layouts are unchanged.
 */
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
            .padding(vertical = 96.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(42.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
internal fun GlassSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val hazeState = LocalHazeState.current
    val isDark = forceDark || MaterialTheme.colorScheme.background.luminance() <= 0.5f
    val isLightTheme = !isDark
    val materialColor = if (forceDark) AuroraInkSurface else MaterialTheme.colorScheme.surface
    val surfaceContentColor = if (forceDark) AuroraInkContent else MaterialTheme.colorScheme.onSurface
    val containerColor = if (forceDark) {
        AuroraInkSurface.copy(alpha = 0.85f)
    } else if (isLightTheme) {
        AuroraGlassContainerLight.copy(alpha = 0.88f)
    } else {
        AuroraGlassContainerDark.copy(alpha = 0.82f)
    }
    val outlineColor = if (forceDark) {
        Color.White.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }
    val glassModifier = if (hazeState != null) {
        Modifier
            .hazeEffect(
                state = hazeState,
                style = HazeMaterials.thin(materialColor),
            ) {
                blurRadius = 24.dp
                noiseFactor = 0.045f
                inputScale = HazeInputScale.Auto
                blurredEdgeTreatment = BlurredEdgeTreatment(shape)
            }
            .background(containerColor, shape)
    } else {
        Modifier.background(containerColor, shape)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .then(glassModifier)
            .border(
                BorderStroke(1.dp, outlineColor),
                shape,
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides surfaceContentColor) {
            content()
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
    content: @Composable () -> Unit,
) {
    val backgroundModifier = if (!applyBackground) {
        Modifier
    } else if (isScrolled) {
        Modifier
            .background(Color(0xFF000000).copy(alpha = 0.94f))
            .border(BorderStroke(0.5.dp, Color(0xFF222222)), RectangleShape)
    } else {
        Modifier.background(
            Brush.verticalGradient(
                listOf(
                    Color.Black.copy(alpha = 0.80f),
                    Color.Black.copy(alpha = 0.35f),
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
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .heightIn(min = 44.dp)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        ) {
            content()
        }
    }
}

@Composable
internal fun NetflixCategoryChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hasDropdown: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val containerColor by animateColorAsState(
        targetValue = if (selected) Color.White else Color(0x66000000),
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.8f),
        label = "chip-bg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Color.Black else Color.White,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.8f),
        label = "chip-fg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) Color.Transparent else Color(0x66FFFFFF),
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.8f),
        label = "chip-border",
    )

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = if (!selected) BorderStroke(1.dp, borderColor) else null,
        modifier = modifier.heightIn(min = 36.dp).applePressEffect(pressedScale = 0.94f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
            if (hasDropdown) {
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
internal fun NetflixNLogo(
    modifier: Modifier = Modifier,
    redColor: Color = AuroraCoral,
    darkRedColor: Color = AuroraBrandAccentDeep,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barW = w * 0.32f

        // Left vertical stem
        drawRect(
            color = darkRedColor,
            topLeft = Offset(0f, 0f),
            size = Size(barW, h),
        )
        // Right vertical stem
        drawRect(
            color = darkRedColor,
            topLeft = Offset(w - barW, 0f),
            size = Size(barW, h),
        )
        // Diagonal ribbon overlapping stems
        val diagonalPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(barW, 0f)
            lineTo(w, h)
            lineTo(w - barW, h)
            close()
        }
        drawPath(
            path = diagonalPath,
            color = redColor,
        )
    }
}

@Composable
internal fun NetflixProfileAvatar(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 28.dp,
    contentDescription: String = "站点设置",
) {
    val haptic = LocalHapticFeedback.current
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            role = Role.Button,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        ).semantics { this.contentDescription = contentDescription }
    } else Modifier

    Box(
        modifier = modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(sizeDp * 0.16f))
            .background(AuroraProfileAccent) // Netflix Profile Blue
            .then(clickableModifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(sizeDp * 0.64f)) {
            val w = size.width
            val h = size.height
            // Eyes
            drawCircle(
                color = Color.White,
                radius = w * 0.08f,
                center = Offset(w * 0.33f, h * 0.36f),
            )
            drawCircle(
                color = Color.White,
                radius = w * 0.08f,
                center = Offset(w * 0.67f, h * 0.36f),
            )
            // Smile arc
            val smilePath = Path().apply {
                moveTo(w * 0.28f, h * 0.58f)
                quadraticTo(
                    w * 0.5f, h * 0.82f,
                    w * 0.72f, h * 0.58f,
                )
            }
            drawPath(
                path = smilePath,
                color = Color.White,
                style = Stroke(width = w * 0.09f, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
internal fun NetflixRankNumber(
    rank: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 84.sp,
    outlineColor: Color = AuroraMediaRankOutline,
    fillColor: Color = AuroraMediaRankFill,
) {
    val rankText = "$rank"
    Box(modifier = modifier) {
        Text(
            text = rankText,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.Black,
            style = TextStyle(
                drawStyle = Stroke(width = 6f, join = StrokeJoin.Round),
            ),
            color = outlineColor,
        )
        Text(
            text = rankText,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.Black,
            color = fillColor,
        )
    }
}

@Composable
internal fun NetflixSectionHeader(
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
            .padding(horizontal = 18.dp, vertical = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (badgeText != null) {
                    Surface(
                        color = AuroraCoral,
                        shape = RoundedCornerShape(3.dp),
                    ) {
                        Text(
                            text = badgeText,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        letterSpacing = (-0.3).sp,
                    ),
                    color = Color.White,
                )
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = AuroraMediaMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (actionText != null && onAction != null) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = AuroraCoral,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(4.dp),
            )
        }
    }
}

@Composable
internal fun NetflixBadge(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = AuroraCoral,
    textColor: Color = Color.White,
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(3.dp),
        modifier = modifier,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
internal fun NetflixPosterShelfCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(114.dp)
            .applePressEffect(onClick = onClick),
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = AuroraMediaPlaceholder,
            modifier = Modifier
                .width(114.dp)
                .height(165.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                MediaImage(
                    video = video,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp),
                ) {
                    NetflixNLogo(modifier = Modifier.size(width = 11.dp, height = 17.dp))
                }
                if (video.duration.isNotBlank()) {
                    Text(
                        text = video.duration,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(AuroraImmersiveBackground.copy(alpha = 0.76f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = AuroraMediaLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = video.views,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = AuroraMediaMeta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun NetflixTopBar(
    modifier: Modifier = Modifier,
    titleTestTag: String? = null,
    onCast: (() -> Unit)? = null,
    isCasting: Boolean = LocalCastController.current.isCasting,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val castController = LocalCastController.current
    val actualOnCast = onCast ?: { castController.onOpenCastSheet() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    ) {
        NetflixNLogo(modifier = Modifier.size(width = 22.dp, height = 34.dp))
        Text(
            text = "Netgrey",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                fontSize = 22.sp,
            ),
            color = AuroraCoral,
            modifier = Modifier
                .semantics { heading() }
                .then(if (titleTestTag != null) Modifier.testTag(titleTestTag) else Modifier),
        )
        Spacer(Modifier.weight(1f))

        IconButton(
            onClick = { actualOnCast() },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = if (isCasting) Icons.Filled.CastConnected else Icons.Outlined.Cast,
                contentDescription = if (isCasting) "正在投屏至電視" else "投屏",
                tint = if (isCasting) AuroraCoral else Color.White,
                modifier = Modifier.size(22.dp),
            )
        }

        actions()
    }
}

@Composable
internal fun NetflixSearchCapsule(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val fontScale = LocalDensity.current.fontScale
    if (fontScale >= 1.3f) {
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            modifier = modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    } else {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF262626).copy(alpha = 0.85f),
            border = BorderStroke(0.8.dp, Color(0xFF454545)),
            modifier = modifier
                .height(36.dp)
                .clickable(
                    role = Role.Button,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    },
                )
                .semantics { contentDescription = label },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = AuroraMediaLabel,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = AuroraMediaLabel,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun CenteredSearchBar(
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
    GlassSurface(
        shape = RoundedCornerShape(24.dp),
        forceDark = true,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBack()
                    },
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = AuroraMediaLabel,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            color = AuroraMediaLabel,
                        ),
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        color = Color.White,
                    ),
                    cursorBrush = SolidColor(AuroraCoral),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClear()
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "清除",
                        tint = AuroraMediaMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            if (isSearching) {
                Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = AuroraCoral,
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSearch()
                    },
                    enabled = query.isNotBlank(),
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "开始搜索",
                        tint = if (query.isNotBlank()) Color.White else Color(0xFF666666),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
