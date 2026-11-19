package com.zensync.mvp.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "tabs")
data class TabEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val url: String,
    val title: String,
    val pinned: Boolean,
    val createdAt: Long,
    val lastActiveAt: Long,
)

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspaces ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces ORDER BY createdAt ASC")
    suspend fun getAll(): List<WorkspaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workspace: WorkspaceEntity)

    @Update
    suspend fun update(workspace: WorkspaceEntity)

    @Delete
    suspend fun delete(workspace: WorkspaceEntity)

    @Query("DELETE FROM tabs WHERE workspaceId = :workspaceId")
    suspend fun deleteTabsForWorkspace(workspaceId: String)
}

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs WHERE workspaceId = :workspaceId ORDER BY pinned DESC, lastActiveAt DESC")
    fun observeForWorkspace(workspaceId: String): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs WHERE id = :id")
    suspend fun getById(id: String): TabEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tab: TabEntity)

    @Update
    suspend fun update(tab: TabEntity)

    @Delete
    suspend fun delete(tab: TabEntity)

    @Query("DELETE FROM tabs")
    suspend fun deleteAll()
}
