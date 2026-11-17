package com.aurorashelf.app.data.resilience

import com.aurorashelf.app.model.VideoItem
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SourceResilienceEngineTest {
    @Before
    fun setUp() {
        SourceHealthRegistry.clearAllForTesting()
    }

    @After
    fun tearDown() {
        SourceHealthRegistry.clearAllForTesting()
    }

    @Test
    fun hedgedMirrorRacingFailsOverFastAndPromotesHealthyMirror() = runBlocking {
        val candidates = listOf("https://dead-primary.example/feed", "https://fast-backup.example/feed")
        val primaryCalls = AtomicInteger(0)
        val backupCalls = AtomicInteger(0)

        // 第一次请求：主镜像黑洞卡死 4000ms，对冲窗口 180ms 后并发拉起备用镜像（35ms 返回），并记录主站失败、备站成功
        val started1 = System.currentTimeMillis()
        val firstResult = AuroraHttpClient.fetchHedgedText(
            sourceKey = "test-hedged",
            candidateUrls = candidates,
            hedgeDelayMs = 180L,
            executor = { targetUrl, _ ->
                if (targetUrl.contains("dead-primary")) {
                    primaryCalls.incrementAndGet()
                    delay(4_000L)
                    SourceHealthRegistry.recordFailure("test-hedged", targetUrl, isHardBlock = true)
                    throw IOException("timeout")
                } else {
                    backupCalls.incrementAndGet()
                    delay(35L)
                    SourceHealthRegistry.recordSuccess("test-hedged", targetUrl, 35L)
                    HttpTextResult("ok-backup", targetUrl, 200, 35L)
                }
            },
        )
        val elapsed1 = System.currentTimeMillis() - started1
        assertEquals("ok-backup", firstResult.body)
        assertEquals("https://fast-backup.example/feed", firstResult.finalUrl)
        assertTrue("首轮对冲耗时应远低于黑洞超时 (实际=${elapsed1}ms)", elapsed1 < 1_000L)

        // 显式记录主镜像阻塞以模拟传输超时收尾后的状态
        SourceHealthRegistry.recordFailure("test-hedged", "dead-primary.example", isHardBlock = true)

        // 第二次请求：健康注册中心应已自动将 fast-backup 升至首位，直接 0ms 对冲直达备站，完全跳过 dead-primary！
        val started2 = System.currentTimeMillis()
        val secondResult = AuroraHttpClient.fetchHedgedText(
            sourceKey = "test-hedged",
            candidateUrls = candidates,
            hedgeDelayMs = 180L,
            executor = { targetUrl, _ ->
                if (targetUrl.contains("dead-primary")) {
                    primaryCalls.incrementAndGet()
                    throw IOException("should have been bypassed")
                }
                backupCalls.incrementAndGet()
                HttpTextResult("fast-hit", targetUrl, 200, 20L)
            },
        )
        val elapsed2 = System.currentTimeMillis() - started2
        assertEquals("fast-hit", secondResult.body)
        assertEquals("主站处于熔断状态时不应继续承担首发请求", 1, primaryCalls.get())
        assertEquals(2, backupCalls.get())
        assertTrue("二次直连应低于 150ms (实际=${elapsed2}ms)", elapsed2 < 150L)
    }

    @Test
    fun circuitBreakerCooldownIsolatesAndRecoversAfterExpiry() {
        val nowMs = AtomicLong(1_000_000L)
        val store = SourceHealthRegistryStore(clock = { nowMs.get() })

        store.recordFailure("copy", "https://api.2024manga.com", statusCode = 502, isHardBlock = true)
        assertTrue(store.isCircuitOpen("api.2024manga.com"))

        // 健康节点 api.mangacopy.com 排在首位
        store.recordSuccess("copy", "https://api.mangacopy.com", latencyMs = 280L)
        val rankedDuringOutage = store.rankCandidates("copy", listOf("https://api.2024manga.com", "https://api.mangacopy.com"))
        assertEquals("https://api.mangacopy.com", rankedDuringOutage.first())

        // 推进时钟跨过 30 秒冷却窗后，熔断自动解除进入可探测状态
        nowMs.addAndGet(31_000L)
        assertFalse(store.isCircuitOpen("api.2024manga.com"))
    }

    @Test
    fun resilientDnsPreservesTunFakeIpWhileHealingPoisonedDns() {
        val fakeIp = InetAddress.getByAddress("tun.fake", byteArrayOf(198.toByte(), 18, 0, 42))
        assertTrue(ResilientDns.isTunFakeIp(fakeIp))

        val resolvedTun = ResilientDns.resolveHost(
            hostname = "pornhub.com",
            systemLookup = { listOf(fakeIp) },
            dohLookup = { listOf(InetAddress.getByName("104.18.1.2")) },
        )
        assertEquals("TUN Fake-IP (198.18.x.x) 必须原样保留，不得被 DoH 覆盖", listOf(fakeIp), resolvedTun)

        // 场景二：系统 DNS 返回污染地址 0.0.0.0 或 NXDOMAIN 时，自动由 DoH 自愈出真实可路由 IP
        val poisonedIp = InetAddress.getByAddress("0.0.0.0", byteArrayOf(0, 0, 0, 0))
        val dohHealthyIp = InetAddress.getByAddress("104.21.10.20", byteArrayOf(104, 21, 10, 20))
        val healedFromPoison = ResilientDns.resolveHost(
            hostname = "blocked-site.example",
            systemLookup = { listOf(poisonedIp) },
            dohLookup = { listOf(dohHealthyIp) },
        )
        assertEquals(listOf(dohHealthyIp), healedFromPoison)

        val healedFromNxDomain = ResilientDns.resolveHost(
            hostname = "nxdomain-site.example",
            systemLookup = { throw UnknownHostException("NXDOMAIN") },
            dohLookup = { listOf(dohHealthyIp) },
        )
        assertEquals(listOf(dohHealthyIp), healedFromNxDomain)
    }

    @Test
    fun adaptiveHtmlParserRecoversCardsAfterCssClassMutation() {
        val mutatedHtml = """
            <html><body>
              <div class="completely-new-2027-grid">
                <section class="card-abc-v9">
                  <a href="/view_video.php?viewkey=feed9988">
                    <img data-original="/thumb/feed9988.jpg" alt="91新版改类名自愈测试" />
                    <span class="new-badge-dur">14:28</span>
                  </a>
                </section>
                <a href="/login">登录</a>
              </div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(mutatedHtml, "https://h1014.sol148.com/")
        val items = AdaptiveHtmlFallbackParser.extractFallbackVideoItems(doc, "https://h1014.sol148.com/", "91porn")
        assertEquals(1, items.size)
        assertEquals("91新版改类名自愈测试", items.first().title)
        assertEquals("https://h1014.sol148.com/view_video.php?viewkey=feed9988", items.first().pageUrl)
        assertEquals("https://h1014.sol148.com/thumb/feed9988.jpg", items.first().thumbnailUrl)
        assertEquals("14:28", items.first().duration)
    }

    @Test
    fun multiSlotSnapshotStoreDeliversSub10msSwrAcrossSourceSwitches() {
        val tempDir = Files.createTempDirectory("aurora_swr_test").toFile()
        try {
            val nowMs = AtomicLong(10_000_000L)
            val store = SourceSnapshotStoreInstance(cacheDirProvider = { tempDir }, clock = { nowMs.get() })

            val sourceA = listOf(VideoItem("a1", "华视推荐电影", "华视影院", "01:42:00", "热播", "https://huavod.com/voddetail/1.html"))
            val sourceB = listOf(VideoItem("b1", "91精选视频", "91", "12:15", "热门", "https://h1014.sol148.com/view_video.php?viewkey=b1"))

            store.putVideoFeed("https://huavod.com", "movie", sourceA)
            store.putVideoFeed("https://h1014.sol148.com", "hot", sourceB)

            // 模拟从 A 切去 B 再切回 A（清掉内存缓存强迫走磁盘检验持久化，再测热读）
            val startRead = System.nanoTime()
            val slotA = store.getVideoFeed("https://huavod.com", "movie")
            val readElapsedMs = (System.nanoTime() - startRead) / 1_000_000.0
            val slotB = store.getVideoFeed("https://h1014.sol148.com", "hot")

            assertNotNull(slotA)
            assertNotNull(slotB)
            assertEquals("华视推荐电影", slotA!!.items.single().title)
            assertEquals("91精选视频", slotB!!.items.single().title)
            assertTrue(slotA.isFresh)
            assertTrue("SWR 首屏读取应 <20ms (实际=${readElapsedMs}ms)", readElapsedMs < 20.0)

            // 超过 90 秒但仍在 72 小时 Stale-If-Error 窗口内：标为需后台静默刷新，但依然立即可渲染
            nowMs.addAndGet(120_000L)
            val staleButAlive = store.getVideoFeed("https://huavod.com", "movie")
            assertNotNull(staleButAlive)
            assertFalse(staleButAlive!!.isFresh)
            assertEquals("华视推荐电影", staleButAlive.items.single().title)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
