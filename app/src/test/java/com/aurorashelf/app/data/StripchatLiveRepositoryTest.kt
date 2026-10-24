package com.aurorashelf.app.data

import com.aurorashelf.app.model.LiveCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StripchatLiveRepositoryTest {
    private val repository = StripchatLiveRepository()

    @Test
    fun parsesOnlyPublicLiveModelsWithSafeFields() {
        val page = repository.parsePage(
            """
            {
              "filteredCount": 81,
              "models": [
                {
                  "id": 42,
                  "username": "live_test_1",
                  "previewUrlThumbSmall": "https://static.example/preview.jpg",
                  "avatarUrl": "https://static.example/avatar.jpg",
                  "viewersCount": 1234,
                  "country": "us",
                  "isHd": true,
                  "isLive": true,
                  "status": "public"
                },
                {
                  "id": 43,
                  "username": "private_test",
                  "previewUrlThumbSmall": "https://static.example/private.jpg",
                  "isLive": true,
                  "status": "private"
                },
                {
                  "id": 44,
                  "username": "offline_test",
                  "previewUrlThumbSmall": "https://static.example/offline.jpg",
                  "isLive": false,
                  "status": "public"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(81, page.totalCount)
        assertEquals(1, page.items.size)
        assertEquals("live_test_1", page.items.single().username)
        assertEquals("US", page.items.single().countryCode)
        assertEquals(1234, page.items.single().viewersCount)
        assertTrue(page.items.single().isHd)
        assertEquals("https://zh.stripchat.com/live_test_1", page.items.single().playerUrl)
    }

    @Test
    fun rejectsUnsafePreviewAndUsername() {
        val page = repository.parsePage(
            """
            {"models":[
              {"id":1,"username":"../escape","previewUrlThumbSmall":"https://static.example/a.jpg","isLive":true,"status":"public"},
              {"id":2,"username":"safe_name","previewUrlThumbSmall":"http://static.example/b.jpg","isLive":true,"status":"public"}
            ]}
            """.trimIndent(),
        )
        assertTrue(page.items.isEmpty())
    }

    @Test
    fun categoryUrlUsesBoundedPagination() {
        val first = repository.buildUrl(LiveCategory.WOMEN, 1)
        val third = repository.buildUrl(LiveCategory.COUPLES, 3)

        assertTrue(first.contains("limit=${StripchatLiveRepository.PAGE_SIZE}"))
        assertTrue(first.contains("offset=0"))
        assertTrue(first.contains("primaryTag=girls"))
        assertTrue(third.contains("offset=${StripchatLiveRepository.PAGE_SIZE * 2}"))
        assertTrue(third.contains("primaryTag=couples"))
    }
}
