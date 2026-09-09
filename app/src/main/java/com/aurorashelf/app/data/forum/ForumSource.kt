package com.aurorashelf.app.data.forum

import com.aurorashelf.app.model.ForumPost

internal interface ForumSource {
    val id: String
    val name: String
    suspend fun load(page: Int): List<ForumPost>
}
