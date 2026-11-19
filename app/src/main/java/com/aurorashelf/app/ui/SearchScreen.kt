@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.aurorashelf.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.VideoItem
import com.aurorashelf.app.ui.theme.AuroraHaptics

@Composable
internal fun SearchScreen(
    query: String,
    videos: List<VideoItem>,
    favoriteIds: Set<String>,
    isSearching: Boolean,
    message: String?,
    filterLocally: Boolean,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onClose: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    onFavorite: (VideoItem) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val matches = remember(query, videos, filterLocally) {
        if (!filterLocally || query.isBlank()) {
            videos
        } else {
            videos.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.author.contains(query, ignoreCase = true) ||
                    ContentSourceCatalog.inferSourceName(it.pageUrl).contains(query, ignoreCase = true)
            }
        }
    }
    var selectedSourceFilter by rememberSaveable { mutableStateOf("全部") }
    val availableSources = remember(matches) {
        if (matches.isEmpty()) listOf("全部")
        else listOf("全部") + matches.map { ContentSourceCatalog.inferSourceName(it.pageUrl) }
            .distinct()
            .sortedWith(compareBy<String> { if (it.contains("华视")) 0 else 1 }.thenBy { it })
    }
    LaunchedEffect(availableSources) {
        if (selectedSourceFilter !in availableSources) {
            selectedSourceFilter = "全部"
        }
    }
    val filteredMatches = remember(matches, selectedSourceFilter) {
        if (selectedSourceFilter == "全部") matches
        else matches.filter { ContentSourceCatalog.inferSourceName(it.pageUrl) == selectedSourceFilter }
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val handleClose: () -> Unit = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onClose()
    }
    val prefetchContext = LocalContext.current
    LaunchedEffect(matches) {
        if (matches.isNotEmpty()) {
            CoverPrefetcher.prefetchVideos(prefetchContext, matches, limit = SEARCH_COVER_PREFETCH_LIMIT)
        }
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            stickyHeader(key = "search-header") {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(modifier = Modifier.padding(bottom = 8.dp)) {
                        AuroraSearchBar(
                            query = query,
                            onQueryChange = onQuery,
                            placeholder = "全局搜索所有视频源",
                            onSearch = onSearch,
                            onBack = handleClose,
                            onClear = { onQuery("") },
                            isSearching = isSearching,
                            testTag = "search-input-field",
                        )
                    }
                }
            }
            message?.let { status -> item { StatusBanner(status, onSearch) } }
            if (isSearching && matches.isEmpty()) {
                item(key = "search-loading") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp),
                    ) {
                        AuroraLoadingIndicator(contained = true)
                    }
                }
            } else if (matches.isEmpty() && !isSearching && message == null) {
                if (query.isBlank()) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        ) {
                            Text(
                                text = "热门探索",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.semantics { heading() },
                            )
                            val trending = listOf(
                                "热门精选", "最新电影", "热门连续剧", "经典港片",
                                "热血动漫", "高分综艺", "悬疑惊悚", "科幻大片",
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                trending.forEach { keyword ->
                                    SuggestionChip(
                                        onClick = {
                                            AuroraHaptics.tap(haptic)
                                            onQuery(keyword)
                                            onSearch()
                                        },
                                        label = { Text(keyword) },
                                        shape = CircleShape,
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            EmptyState(
                                message = "输入关键词全局搜索所有视频源",
                                icon = Icons.Outlined.Search,
                                actionLabel = null,
                                onAction = null,
                            )
                        }
                    }
                } else {
                    item {
                        EmptyState(
                            message = "点击搜索按钮开始查找",
                            icon = Icons.Outlined.Search,
                            actionLabel = if (!isSearching) "开始搜索" else null,
                            onAction = onSearch,
                        )
                    }
                }
            } else {
                if (availableSources.size > 2) {
                    item(key = "source-filter-bar") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            availableSources.forEach { src ->
                                val count = if (src == "全部") {
                                    matches.size
                                } else {
                                    matches.count { ContentSourceCatalog.inferSourceName(it.pageUrl) == src }
                                }
                                AuroraFilterChip(
                                    selected = selectedSourceFilter == src,
                                    onClick = { selectedSourceFilter = src },
                                    label = "$src ($count)",
                                )
                            }
                        }
                    }
                }

                val topMatches = filteredMatches.take(4)
                if (topMatches.isNotEmpty()) {
                    item(key = "top-matches-shelf") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "热门匹配",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.semantics { heading() },
                                )
                                AuroraBadge(text = "精选 ${topMatches.size} 部")
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            ) {
                                topMatches.forEach { video ->
                                    AuroraPosterCard(
                                        video = video,
                                        onClick = { onVideo(video) },
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "all-results-header") {
                    Text(
                        text = "全部检索结果 (${filteredMatches.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .semantics { heading() },
                    )
                }

                items(filteredMatches, key = VideoItem::id) { video ->
                    VideoListRow(
                        video = video,
                        isFavorite = video.id in favoriteIds,
                        onClick = { onVideo(video) },
                        onFavorite = { onFavorite(video) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

private const val SEARCH_COVER_PREFETCH_LIMIT = 8
