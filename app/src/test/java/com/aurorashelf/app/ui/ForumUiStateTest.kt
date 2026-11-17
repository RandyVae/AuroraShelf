package com.aurorashelf.app.ui

import com.aurorashelf.app.model.ForumPost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumUiStateTest {
    @Test
    fun defaultStateHasNoErrorsAndCanLoadMore() {
        val state = ForumUiState()
        assertNull(state.error)
        assertNull(state.loadMoreError)
        assertTrue(state.posts.isEmpty())
        assertFalse(state.isLoading)
        assertFalse(state.isLoadingMore)
        assertTrue(state.canLoadMore)
        assertNull(state.selectedSourceId)
    }

    @Test
    fun sourceFilterCanBeSelectedAndToggled() {
        val state = ForumUiState()
        val with91 = state.copy(selectedSourceId = "t906")
        assertEquals("t906", with91.selectedSourceId)
        val withT66y = with91.copy(selectedSourceId = "t66y")
        assertEquals("t66y", withT66y.selectedSourceId)
        val cleared = withT66y.copy(selectedSourceId = null)
        assertNull(cleared.selectedSourceId)
    }

    @Test
    fun refreshFailurePreservesPostsWhileSettingTopLevelError() {
        val post = ForumPost("test", "测试", "1", "标题", "作者", 0, "刚刚", 0, "https://example.com")
        val state = ForumUiState(posts = listOf(post), page = 1)

        val updated = state.copy(isLoading = false, error = "网络连接失败")
        assertEquals(1, updated.posts.size)
        assertEquals("网络连接失败", updated.error)
        assertNull(updated.loadMoreError)
    }

    @Test
    fun loadMoreFailureSetsIsolatedLoadMoreError() {
        val post = ForumPost("test", "测试", "1", "标题", "作者", 0, "刚刚", 0, "https://example.com")
        val state = ForumUiState(posts = listOf(post), page = 1)

        val updated = state.copy(isLoadingMore = false, loadMoreError = "下一页加载失败")
        assertEquals(1, updated.posts.size)
        assertNull(updated.error)
        assertEquals("下一页加载失败", updated.loadMoreError)
    }

    @Test
    fun forumReaderRevealPolicyRevealsAtProgressThresholdWithoutDuplicates() {
        assertFalse(ForumReaderRevealPolicy.shouldRevealOnProgress(progress = 25, generation = 2, styledGeneration = -1))
        assertTrue(ForumReaderRevealPolicy.shouldRevealOnProgress(progress = 45, generation = 2, styledGeneration = -1))
        assertFalse(ForumReaderRevealPolicy.shouldRevealOnProgress(progress = 90, generation = 2, styledGeneration = 2))
    }

    @Test
    fun mobileForumScriptEnforcesVisibleScrollingAndHighContrastDarkTheme() {
        val darkScript = mobileForumScript(isDarkTheme = true)
        assertTrue(darkScript.contains("const auroraDark = true;"))
        assertTrue(darkScript.contains("overflow-y: visible !important;"))
        assertTrue(darkScript.contains("touch-action: pan-y pinch-zoom !important;"))
        assertTrue(darkScript.contains("document.getElementById('aurora-mobile-style')"))
        assertTrue(darkScript.contains("body.aurora-dark .tpc_content"))
        assertTrue(darkScript.contains("body.aurora-dark .postmessage"))
        assertTrue(darkScript.contains("color: #e6e0e9 !important;"))
    }

    @Test
    fun heroPresentationStyleAlwaysUsesFullBleedAcrossAspectRatios() {
        assertEquals(
            HeroPresentationStyle.FULL_BLEED,
            resolveHeroPresentationStyle(2560, 650, 1080, 1208),
        )
        assertEquals(
            HeroPresentationStyle.FULL_BLEED,
            resolveHeroPresentationStyle(1280, 720, 1080, 1208),
        )
        assertEquals(
            HeroPresentationStyle.FULL_BLEED,
            resolveHeroPresentationStyle(362, 678, 1080, 1208),
        )
        assertEquals(
            HeroPresentationStyle.FULL_BLEED,
            resolveHeroPresentationStyle(1080, 1512, 1080, 1208),
        )
    }
}
