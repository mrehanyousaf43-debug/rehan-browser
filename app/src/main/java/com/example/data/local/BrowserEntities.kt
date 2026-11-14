package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class WorkspaceType(val label: String) {
    RESEARCH("Research"),
    EDITING("Editing"),
    ENTERTAINMENT("Entertainment")
}

@Entity(tableName = "tabs")
data class TabEntity(
    @PrimaryKey val id: String,
    val workspace: String = WorkspaceType.RESEARCH.name,
    val url: String = "about:newtab",
    val title: String = "New Tab",
    val isIncognito: Boolean = false,
    val isPinned: Boolean = false,
    val groupName: String? = null,
    val groupColorHex: Long? = null,
    val position: Int = 0,
    val lastAccessedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val workspace: String = WorkspaceType.RESEARCH.name,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val workspace: String = WorkspaceType.RESEARCH.name,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systemDownloadId: Long = -1L,
    val fileName: String,
    val url: String,
    val mimeType: String = "application/octet-stream",
    val status: String = "Downloading",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "speed_dials")
data class SpeedDialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val accentHex: Long = 0xFF00E5FF,
    val position: Int = 0
)

@Entity(tableName = "site_settings")
data class SiteSettingsEntity(
    @PrimaryKey val host: String,
    val adBlockEnabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
