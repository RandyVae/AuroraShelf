package com.aurorashelf.app.model

data class ComicSummary(
    val sourceId: String,
    val id: String,
    val title: String,
    val subtitle: String = "",
    val coverUrl: String? = null,
    val tags: List<String> = emptyList(),
)

data class ComicChapter(
    val id: String,
    val title: String,
)

data class ComicDetails(
    val comic: ComicSummary,
    val description: String = "",
    val chapters: List<ComicChapter> = emptyList(),
)

data class ComicPage(
    val imageUrl: String,
    val referer: String,
)

data class ComicCategory(
    val id: String,
    val label: String,
)

data class ComicSourceInfo(
    val id: String,
    val name: String,
    val description: String,
    val categories: List<ComicCategory>,
)
