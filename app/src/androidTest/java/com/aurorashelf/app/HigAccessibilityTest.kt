package com.aurorashelf.app

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.aurorashelf.app.data.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

/**
 * HIG 无障碍自动化验收：底栏 48dp 触控目标、页面/分区 heading 语义、关键交互节点标签。
 * 只做静态语义断言：不等待网络数据、不触发播放。
 */
class HigAccessibilityTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.getSharedPreferences("aurora_shelf", Context.MODE_PRIVATE).edit()
                .clear().putString("source_url", AppPreferences.DEFAULT_SOURCE).commit()
        }
    }).around(compose)

    @Test
    fun bottomNavigationTabsMeet48DpTouchTarget() {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(
                hasClickAction() and hasAnyAncestor(hasTestTag("liquid-navigation")),
            ).fetchSemanticsNodes().size == 5
        }
        val minTouchTargetPx = with(compose.density) { 48.dp.toPx() }
        val tabs = compose.onAllNodes(
            hasClickAction() and hasAnyAncestor(hasTestTag("liquid-navigation")),
        ).fetchSemanticsNodes()

        assertEquals("悬浮底栏应有 5 个可点击导航项", 5, tabs.size)
        tabs.forEachIndexed { index, node ->
            val bounds = node.boundsInRoot
            assertTrue(
                "底栏第 ${index + 1} 项宽度 ${bounds.width}px 应 ≥ 48dp(${minTouchTargetPx}px)",
                bounds.width >= minTouchTargetPx,
            )
            assertTrue(
                "底栏第 ${index + 1} 项高度 ${bounds.height}px 应 ≥ 48dp(${minTouchTargetPx}px)",
                bounds.height >= minTouchTargetPx,
            )
        }
    }

    @Test
    fun homeAndPersonalPagesExposeHeadingSemantics() {
        // Brand copy is owned by the product and can change; only require that the home page
        // exposes at least one heading so screen-reader users can jump to the page structure.
        val homeHeadings = compose.onAllNodes(isHeading()).fetchSemanticsNodes()
        assertTrue("主页应至少存在一个 heading 语义节点", homeHeadings.isNotEmpty())

        compose.onNodeWithContentDescription("我的").performClick()
        compose.onNodeWithTag("personal-overview").assertExists()
        compose.onNode(hasText("我的") and isHeading()).assertExists()
    }

    @Test
    fun keyInteractiveNodesExposeLabels() {
        listOf("首页", "漫画", "直播", "论坛", "我的").forEach { label ->
            compose.onNodeWithContentDescription(label).assertExists()
        }
        compose.onNodeWithContentDescription("站点设置").assertExists()
        compose.onNodeWithText("搜索视频").assertExists()

        val bottomTabs = compose.onAllNodes(
            hasClickAction() and hasAnyAncestor(hasTestTag("liquid-navigation")),
        ).fetchSemanticsNodes()
        bottomTabs.forEachIndexed { index, node ->
            val hasDescription = node.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.any { it.isNotBlank() } == true
            val hasVisibleLabel = node.config.getOrNull(SemanticsProperties.Text)
                ?.any { it.text.isNotBlank() } == true
            assertTrue(
                "底栏第 ${index + 1} 项缺少可读标签（contentDescription/文本）",
                hasDescription || hasVisibleLabel,
            )
        }
    }
}
