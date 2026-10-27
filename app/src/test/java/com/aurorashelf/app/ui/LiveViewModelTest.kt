package com.aurorashelf.app.ui

import com.aurorashelf.app.model.LiveItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveViewModelTest {
    @Test
    fun allViewModelsHaveSingleApplicationConstructorForViewModelProvider() {
        assertNotNull(LiveViewModel::class.java.getConstructor(android.app.Application::class.java))
        assertNotNull(AuroraViewModel::class.java.getConstructor(android.app.Application::class.java))
        assertNotNull(ComicViewModel::class.java.getConstructor(android.app.Application::class.java))
        assertNotNull(ForumViewModel::class.java.getConstructor(android.app.Application::class.java))
    }

    @Test
    fun liveSourceCatalogProvidesStripchatAndHornyStudents() {
        val sources = com.aurorashelf.app.model.LiveSourceCatalog.sources
        assertEquals(2, sources.size)
        assertEquals("stripchat", sources[0].id)
        assertEquals("Stripchat", sources[0].name)
        assertEquals("hornystudents", sources[1].id)
        assertEquals("HornyStudents", sources[1].name)
    }

    @Test
    fun emptyIncomingPageStopsPagination() {
        val existing = items("1", "2", "3")

        val update = LiveViewModel.nextLivePage(
            currentItems = existing,
            incomingItems = emptyList(),
            totalCount = 900,
            replace = false,
        )

        assertEquals(existing.map(LiveItem::id), update.items.map(LiveItem::id))
        assertFalse(update.canLoadMore)
    }

    @Test
    fun pageWithOnlySeenItemsStopsPagination() {
        val existing = items("1", "2", "3")

        val update = LiveViewModel.nextLivePage(
            currentItems = existing,
            incomingItems = items("3", "2", "1", "2"),
            totalCount = 900,
            replace = false,
        )

        assertEquals(listOf("1", "2", "3"), update.items.map(LiveItem::id))
        assertFalse(update.canLoadMore)
    }

    @Test
    fun pageWithSomeNewItemsKeepsPaginationAlive() {
        val update = LiveViewModel.nextLivePage(
            currentItems = items("1", "2"),
            incomingItems = items("2", "3", "3"),
            totalCount = 900,
            replace = false,
        )

        assertEquals(listOf("1", "2", "3"), update.items.map(LiveItem::id))
        assertTrue(update.canLoadMore)
    }

    @Test
    fun pageReachingTotalCountStopsPagination() {
        val update = LiveViewModel.nextLivePage(
            currentItems = items("1", "2", "3", "4"),
            incomingItems = items("5", "6"),
            totalCount = 6,
            replace = false,
        )

        assertEquals(6, update.items.size)
        assertFalse(update.canLoadMore)
    }

    @Test
    fun replaceUsesOnlyIncomingPage() {
        val update = LiveViewModel.nextLivePage(
            currentItems = items("1", "2"),
            incomingItems = items("3", "4"),
            totalCount = 900,
            replace = true,
        )

        assertEquals(listOf("3", "4"), update.items.map(LiveItem::id))
        assertTrue(update.canLoadMore)
    }

    @Test
    fun replaceWithEmptyPageStopsPagination() {
        val update = LiveViewModel.nextLivePage(
            currentItems = items("1"),
            incomingItems = emptyList(),
            totalCount = 900,
            replace = true,
        )

        assertTrue(update.items.isEmpty())
        assertFalse(update.canLoadMore)
    }

    private fun items(vararg ids: String): List<LiveItem> = ids.map { id -> liveItem(id) }

    private fun liveItem(id: String) = LiveItem(
        id = id,
        username = "user-$id",
        thumbnailUrl = "https://zh.stripchat.com/$id.jpg",
        viewersCount = 0,
        countryCode = "US",
        isHd = false,
        playerUrl = "https://zh.stripchat.com/user-$id",
    )
}
