package com.aurorashelf.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.aurorashelf.app.model.ForumPost
import com.aurorashelf.app.ui.ForumScreen
import com.aurorashelf.app.ui.ForumUiState
import org.junit.Rule
import org.junit.Test

class ForumScrollRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun closingReaderPreservesListPosition() {
        val posts = List(60) { index ->
            ForumPost("fixture", "测试来源", "$index", "测试帖子 $index", "作者", 0,
                "今天", 0, "https://example.invalid/$index")
        }
        val state = mutableStateOf(ForumUiState(posts = posts, page = 1, canLoadMore = false))
        compose.setContent {
            ForumScreen(state.value, 0.dp, {}, {}, {},
                { state.value = state.value.copy(selectedPost = it) },
                { state.value = state.value.copy(selectedPost = null) })
        }
        compose.onNodeWithTag("forum-feed").performScrollToIndex(21)
        compose.onNodeWithText("测试帖子 20").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(selectedPost = posts[20]) }
        compose.onNodeWithTag("forum-reader").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(selectedPost = null) }
        compose.onNodeWithText("测试帖子 20").assertIsDisplayed()
    }
}
