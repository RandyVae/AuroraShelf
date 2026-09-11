package com.aurorashelf.app

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import com.aurorashelf.app.ui.mobileForumScript
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses neutral local fixtures; never fetches forum content. */
class ForumReaderRegressionTest {
    @Test fun paginationSurvivesReaderCleanup() {
        verifyPagination("https://t66y.com/", "main", "t3", "t2")
        verifyPagination("https://t906.example/", "wrap", "forumcontrol", "postlist")
    }

    private fun verifyPagination(base: String, wrapper: String, controls: String, posts: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val complete = CountDownLatch(1)
        val result = AtomicReference<String>()
        lateinit var webView: WebView
        instrumentation.runOnMainSync {
            webView = WebView(instrumentation.targetContext).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(mobileForumScript(isDarkTheme = false)) {
                            view.evaluateJavascript(
                                """(() => {
                                  const link = document.getElementById('next');
                                  const controls = document.querySelector('.$controls');
                                  const root = document.getElementById('$wrapper');
                                  let node = link;
                                  while (node) {
                                    if (getComputedStyle(node).display === 'none') return false;
                                    node = node.parentElement;
                                  }
                                  return link.getAttribute('href') === '?page=2' &&
                                    document.querySelector('.aurora-pages') !== null &&
                                    getComputedStyle(controls).display === 'none' &&
                                    getComputedStyle(root).backgroundColor === 'rgba(0, 0, 0, 0)' &&
                                    document.documentElement.scrollWidth <= window.innerWidth;
                                })()""".trimIndent(),
                            ) { value -> result.set(value); complete.countDown() }
                        }
                    }
                }
                loadDataWithBaseURL(base, """
                    <html><head></head><body><div id="$wrapper" style="background: pink">
                    <div class="$controls"><div class="pages"><a id="next" href="?page=2">下一页</a></div></div>
                    <div id="$posts" class="$posts">测试正文</div>
                    </div></body></html>
                """.trimIndent(), "text/html", "UTF-8", null)
            }
        }
        try {
            assertTrue("Reader fixture timed out", complete.await(15, TimeUnit.SECONDS))
            assertEquals("Pagination hidden for $wrapper", "true", result.get())
        } finally {
            instrumentation.runOnMainSync { webView.destroy() }
        }
    }
}
