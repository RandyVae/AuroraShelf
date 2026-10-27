package com.aurorashelf.app.data

import com.aurorashelf.app.model.LiveCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HornyStudentsLiveRepositoryTest {
    private val repository = HornyStudentsLiveRepository()

    private val sampleRss = """
        <?xml version="1.0" encoding="utf-8"?>
        <rss version="2.0" xmlns:cb="https://chaturbate.com/feed/namespace/">
        <channel>
        <title>Featured Latest online cams</title>
        <item>
            <title>seduza_hyun</title>
            <cb:username>seduza_hyun</cb:username>
            <cb:gender>f</cb:gender>
            <cb:location>Seoul, KR</cb:location>
            <cb:image_url>https://thumb.live.mmcdn.com/ri/seduza_hyun.jpg</cb:image_url>
            <cb:number_of_users_watching>320</cb:number_of_users_watching>
            <cb:current_show>public</cb:current_show>
        </item>
        <item>
            <title>campus_couple</title>
            <cb:username>campus_couple</cb:username>
            <cb:gender>c</cb:gender>
            <cb:location>California, US</cb:location>
            <cb:image_url>https://thumb.live.mmcdn.com/ri/campus_couple.jpg</cb:image_url>
            <cb:number_of_users_watching>512</cb:number_of_users_watching>
            <cb:current_show>public</cb:current_show>
        </item>
        <item>
            <title>college_boy</title>
            <cb:username>college_boy</cb:username>
            <cb:gender>m</cb:gender>
            <cb:location>London, GB</cb:location>
            <cb:image_url>https://thumb.live.mmcdn.com/ri/college_boy.jpg</cb:image_url>
            <cb:number_of_users_watching>88</cb:number_of_users_watching>
            <cb:current_show>public</cb:current_show>
        </item>
        <item>
            <title>trans_beauty</title>
            <cb:username>trans_beauty</cb:username>
            <cb:gender>s</cb:gender>
            <cb:location>Berlin, DE</cb:location>
            <cb:image_url>https://thumb.live.mmcdn.com/ri/trans_beauty.jpg</cb:image_url>
            <cb:number_of_users_watching>150</cb:number_of_users_watching>
            <cb:current_show>public</cb:current_show>
        </item>
        <item>
            <title>private_cam</title>
            <cb:username>private_cam</cb:username>
            <cb:gender>f</cb:gender>
            <cb:location>Tokyo, JP</cb:location>
            <cb:image_url>https://thumb.live.mmcdn.com/ri/private_cam.jpg</cb:image_url>
            <cb:number_of_users_watching>10</cb:number_of_users_watching>
            <cb:current_show>private</cb:current_show>
        </item>
        </channel>
        </rss>
    """.trimIndent()

    @Test
    fun parsesPublicCamsAndDropsPrivateCams() {
        val rawItems = repository.parseFeed(sampleRss)
        assertEquals(4, rawItems.size)
        assertEquals("seduza_hyun", rawItems[0].username)
        assertEquals("f", rawItems[0].gender)
        assertEquals(320, rawItems[0].viewersCount)
        assertEquals("KR", rawItems[0].countryCode)
        assertEquals("https://thumb.live.mmcdn.com/ri/seduza_hyun.jpg", rawItems[0].thumbnailUrl)
    }

    @Test
    fun filtersCorrectlyByAllFourCategories() {
        val rawItems = repository.parseFeed(sampleRss)

        val women = repository.filterByCategory(rawItems, LiveCategory.WOMEN)
        assertEquals(1, women.size)
        assertEquals("seduza_hyun", women.first().username)

        val couples = repository.filterByCategory(rawItems, LiveCategory.COUPLES)
        assertEquals(1, couples.size)
        assertEquals("campus_couple", couples.first().username)

        val men = repository.filterByCategory(rawItems, LiveCategory.MEN)
        assertEquals(1, men.size)
        assertEquals("college_boy", men.first().username)

        val trans = repository.filterByCategory(rawItems, LiveCategory.TRANS)
        assertEquals(1, trans.size)
        assertEquals("trans_beauty", trans.first().username)
    }

    @Test
    fun rawItemToLiveItemMapsPlayerUrlProperly() {
        val raw = HornyStudentsLiveRepository.LiveItemRaw(
            username = "student_girl",
            gender = "f",
            thumbnailUrl = "https://thumb.live.mmcdn.com/ri/student_girl.jpg",
            viewersCount = 99,
            countryCode = "US",
        )
        val item = raw.toLiveItem()
        assertEquals("hs-student_girl", item.id)
        assertEquals("student_girl", item.username)
        assertEquals("https://www.hornystudents.com/student_girl", item.playerUrl)
        assertTrue(item.isHd)
    }
}
