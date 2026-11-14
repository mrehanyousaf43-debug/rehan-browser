package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs WHERE workspace = :workspace ORDER BY isPinned DESC, position ASC")
    fun observeTabsForWorkspace(workspace: String): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs ORDER BY isPinned DESC, position ASC")
    fun observeAllTabs(): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs WHERE workspace = :workspace ORDER BY isPinned DESC, position ASC")
    suspend fun getTabsForWorkspace(workspace: String): List<TabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTab(tab: TabEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTabs(tabs: List<TabEntity>)

    @Update
    suspend fun updateTab(tab: TabEntity)

    @Query("DELETE FROM tabs WHERE id = :tabId")
    suspend fun deleteTab(tabId: String)

    @Query("DELETE FROM tabs WHERE isIncognito = 1")
    suspend fun deleteAllIncognitoTabs()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    suspend fun isBookmarked(url: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE workspace = :workspace ORDER BY visitedAt DESC LIMIT 300")
    fun observeHistoryForWorkspace(workspace: String): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 300")
    fun observeAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE workspace = :workspace AND (title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%') ORDER BY visitedAt DESC LIMIT 8")
    suspend fun searchHistory(workspace: String, query: String): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history WHERE workspace = :workspace")
    suspend fun clearWorkspaceHistory(workspace: String)

    @Query("DELETE FROM history")
    suspend fun clearAllHistory()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeDownloads(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads")
    suspend fun clearAll()
}

@Dao
interface SpeedDialDao {
    @Query("SELECT * FROM speed_dials ORDER BY position ASC")
    fun observeSpeedDials(): Flow<List<SpeedDialEntity>>

    @Query("SELECT COUNT(*) FROM speed_dials")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(speedDial: SpeedDialEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(speedDials: List<SpeedDialEntity>)

    @Query("DELETE FROM speed_dials WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface SiteSettingsDao {
    @Query("SELECT * FROM site_settings")
    fun observeAllSiteSettings(): Flow<List<SiteSettingsEntity>>

    @Query("SELECT * FROM site_settings WHERE host = :host LIMIT 1")
    suspend fun getForHost(host: String): SiteSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: SiteSettingsEntity)
}
