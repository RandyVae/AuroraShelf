package com.aurorashelf.app.data.forum

import com.aurorashelf.app.model.ForumPost
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumRepositoryTest {
    @Test
    fun firstSourceArrivesBeforeTheSlowSourceFinishes() = runBlocking {
        val slowGate = CompletableDeferred<Unit>()
        val fast = ControlledSource("fast", "快源", posts = listOf(post("fast", "1", 100)))
        val slow = ControlledSource(
            id = "slow",
            name = "慢源",
            posts = listOf(post("slow", "2", 200)),
            beforeReturn = { slowGate.await() },
        )
        val updates = mutableListOf<List<ForumPost>>()

        val job = async {
            ForumRepository(listOf(fast, slow)).loadProgressively(1) { posts, _, _ ->
                updates += posts
            }
        }
        withTimeout(5_000) {
            while (updates.isEmpty()) yield()
        }

        assertEquals("fast source should paint first", listOf("1"), updates.first().map(ForumPost::id))
        assertTrue("slow source must still be pending", slowGate.isActive)

        slowGate.complete(Unit)
        val merged = job.await()

        assertEquals(listOf("2", "1"), merged.posts.map(ForumPost::id))
    }

    @Test
    fun mergesDeDuplicatesAndSortsNewestFirst() = runBlocking {
        val first = ControlledSource(
            id = "one",
            name = "来源一",
            posts = listOf(post("one", "1", 100), post("one", "1", 999)),
        )
        val second = ControlledSource("two", "来源二", posts = listOf(post("two", "2", 200)))
        val updates = mutableListOf<List<ForumPost>>()

        val page = ForumRepository(listOf(first, second)).loadProgressively(1) { posts, _, _ ->
            updates += posts
        }

        assertEquals(listOf("2", "1"), page.posts.map(ForumPost::id))
        assertEquals("duplicate (sourceId, id) must be collapsed", 2, page.posts.size)
        assertEquals("one callback per completed source", 2, updates.size)
        assertTrue(page.unavailableSources.isEmpty())
        assertTrue(page.canLoadMore)
    }

    @Test
    fun keepsWorkingSourceWhenOtherSourceFails() = runBlocking {
        val working = ControlledSource("one", "来源一", posts = listOf(post("one", "1", 100)))
        val failing = ControlledSource("two", "来源二", failure = IOException("offline"))

        val page = ForumRepository(listOf(working, failing)).loadProgressively(1) { _, _, _ -> }

        assertEquals(listOf("1"), page.posts.map(ForumPost::id))
        assertEquals(listOf("来源二"), page.unavailableSources)
        assertTrue(page.canLoadMore)
    }

    @Test
    fun throwsWhenEverySourceFails() = runBlocking {
        val first = ControlledSource("one", "来源一", failure = IOException("offline-one"))
        val second = ControlledSource("two", "来源二", failure = IOException("offline-two"))

        val failure = runCatching {
            ForumRepository(listOf(first, second)).loadProgressively(1) { _, _, _ -> }
        }.exceptionOrNull()

        assertTrue("aggregate failure must surface", failure is IOException)
        assertEquals("offline-one", failure?.message)
    }

    private fun post(sourceId: String, id: String, updatedEpochSeconds: Long) = ForumPost(
        sourceId = sourceId,
        sourceName = sourceId,
        id = id,
        title = "标题 $id",
        author = "作者",
        replyCount = 0,
        updatedLabel = "刚刚",
        updatedEpochSeconds = updatedEpochSeconds,
        url = "https://example.com/$sourceId/$id",
    )

    private class ControlledSource(
        override val id: String,
        override val name: String,
        private val posts: List<ForumPost> = emptyList(),
        private val failure: Throwable? = null,
        private val beforeReturn: suspend () -> Unit = {},
    ) : ForumSource {
        override suspend fun load(page: Int): List<ForumPost> {
            beforeReturn()
            failure?.let { throw it }
            return posts
        }
    }
}
