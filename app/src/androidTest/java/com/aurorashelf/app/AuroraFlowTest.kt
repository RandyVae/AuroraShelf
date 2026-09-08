package com.aurorashelf.app

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.aurorashelf.app.data.AppPreferences
import com.aurorashelf.app.model.ContentSourceCatalog
import com.aurorashelf.app.model.VideoItem
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

class AuroraFlowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: TestRule = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.getSharedPreferences("aurora_shelf", Context.MODE_PRIVATE).edit()
                .clear().putString("source_url", AppPreferences.DEFAULT_SOURCE).commit()
            val savedVideo = VideoItem(
                id = "test-saved-favorite",
                title = "本地收藏流程测试",
                author = "test",
                duration = "01:00",
                views = "1",
                pageUrl = "https://example.com/video/test-saved-favorite",
            )
            AppPreferences(context).apply {
                toggleFavorite(savedVideo.id)
                saveVideo(savedVideo)
            }
        }
    }).around(compose)

    @Test fun searchShowsEmptyStateForImpossibleQuery() {
        compose.onNodeWithText("搜索视频").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("no-match-aurora-8123")
        compose.onNodeWithContentDescription("开始搜索").performClick()
        compose.onNodeWithText("已加载内容中没有匹配结果").assertExists()
    }

    @Test fun settingsValidatesAddressAndShowsOfflineCache() {
        compose.onNodeWithContentDescription("站点设置").performClick()
        val customAddressIndex = 2 + ContentSourceCatalog.sources.size
        compose.onNodeWithTag("settings-list").performScrollToIndex(customAddressIndex)
        compose.onNode(hasSetTextAction()).performTextReplacement("javascript:alert(1)")
        compose.onNodeWithText("保存并刷新").assertIsNotEnabled()
        compose.onNodeWithText("离线缓存").performScrollTo().assertExists()
        compose.onNodeWithText("减少透明度").assertDoesNotExist()
        compose.onNodeWithText("隐私与安全").assertDoesNotExist()
    }

    @Test fun favoriteAppearsInFavorites() {
        compose.onNodeWithContentDescription("收藏").performClick()
        compose.onNodeWithText("我的收藏").assertExists()
        compose.onNodeWithContentDescription("取消收藏").assertExists()
    }

    @Test fun comicsDestinationShowsSourcesAndCategories() {
        compose.onNodeWithContentDescription("漫画").performClick()
        compose.onNodeWithTag("comic-library-title").assertExists()
        compose.onNodeWithText("包子漫画").assertExists()
        compose.onNodeWithText("全部").assertExists()
        compose.onNodeWithText("搜索漫画").assertExists()
    }

    @Test fun remoteLibrarySurvivesRepositoryRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = AppPreferences(context)
        val video = VideoItem("test-persist", "持久化测试", "author", "01:00", "12", "https://example.com/video")
        prefs.addToHistory(video.id)
        prefs.saveVideo(video)
        assertEquals(video, AppPreferences(context).savedVideos().first { it.id == video.id })
    }

    @Test fun feedContinuesBehindFloatingNavigation() {
        val feedBounds = compose.onNodeWithTag("home-feed").getUnclippedBoundsInRoot()
        val dockBounds = compose.onNodeWithTag("bottom-dock").getUnclippedBoundsInRoot()
        assertTrue("Feed viewport must continue behind the floating dock", feedBounds.bottom > dockBounds.top)
    }

    @Test fun homeFeedPairsVideosInTwoColumnsAfterHero() {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithTag("home-grid-card").fetchSemanticsNodes().size >= 2
        }
        val cards = compose.onAllNodesWithTag("home-grid-card").fetchSemanticsNodes()
        assertEquals(cards[0].boundsInRoot.top, cards[1].boundsInRoot.top, 1f)
        assertTrue("Grid cards must occupy separate columns", cards[0].boundsInRoot.right <= cards[1].boundsInRoot.left)
    }

    @Test fun bottomNavigationSupportsDragSelection() {
        compose.onNodeWithTag("liquid-navigation").performTouchInput {
            swipe(
                start = centerLeft + Offset(20f, 0f),
                end = centerRight - Offset(20f, 0f),
                durationMillis = 1_200,
            )
        }
        compose.onNodeWithText("内容源与离线缓存").assertExists()
    }

    @Test fun playerFullscreenHidesDetailsAndBackReturnsToDetails() {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithTag("home-grid-card").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithTag("home-grid-card")[0].performClick()
        compose.onNodeWithContentDescription("全屏播放").assertIsDisplayed().performClick()
        compose.onNodeWithTag("player-details").assertDoesNotExist()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.onNodeWithTag("player-details").assertExists()
        compose.onNodeWithContentDescription("全屏播放").assertIsDisplayed()
    }
}
