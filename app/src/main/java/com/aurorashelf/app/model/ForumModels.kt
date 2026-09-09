package com.aurorashelf.app.model

data class ForumPost(
    val sourceId: String,
    val sourceName: String,
    val id: String,
    val title: String,
    val author: String,
    val replyCount: Int,
    val updatedLabel: String,
    val updatedEpochSeconds: Long,
    val url: String,
)

data class ForumPage(
    val posts: List<ForumPost>,
    val unavailableSources: List<String> = emptyList(),
    val canLoadMore: Boolean = true,
)
