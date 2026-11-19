package com.zensync.mvp.domain.repo

import com.zensync.mvp.data.local.AppDatabase
import com.zensync.mvp.data.local.TabEntity
import com.zensync.mvp.data.local.WorkspaceEntity
import com.zensync.mvp.domain.model.BrowserTab
import com.zensync.mvp.domain.model.Workspace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private fun WorkspaceEntity.toModel() = Workspace(id, name, createdAt = createdAt)
private fun Workspace.toEntity() = WorkspaceEntity(id, name, createdAt)
private fun TabEntity.toModel() = BrowserTab(id, workspaceId, url, title, pinned, createdAt, lastActiveAt)
private fun BrowserTab.toEntity() = TabEntity(id, workspaceId, url, title, pinned, createdAt, lastActiveAt)

class WorkspaceRepository(private val db: AppDatabase) {
    fun observeAll(): Flow<List<Workspace>> = db.workspaceDao().observeAll().map { list -> list.map { it.toModel() } }
    suspend fun getAll(): List<Workspace> = db.workspaceDao().getAll().map { it.toModel() }
    suspend fun create(name: String): Workspace {
        val ws = Workspace(name = name.trim().ifEmpty { "Workspace" })
        db.workspaceDao().insert(ws.toEntity())
        return ws
    }
    suspend fun rename(workspace: Workspace, newName: String) {
        db.workspaceDao().update(workspace.copy(name = newName.trim()).toEntity())
    }
    suspend fun delete(workspace: Workspace) {
        db.workspaceDao().deleteTabsForWorkspace(workspace.id)
        db.workspaceDao().delete(workspace.toEntity())
    }
    /** Creates the default workspace on first launch. Returns its id. */
    suspend fun ensureDefault(): Workspace {
        val existing = getAll()
        if (existing.isNotEmpty()) return existing.first()
        return create("Main")
    }
}

class TabRepository(private val db: AppDatabase) {
    fun observeForWorkspace(workspaceId: String): Flow<List<BrowserTab>> =
        db.tabDao().observeForWorkspace(workspaceId).map { list -> list.map { it.toModel() } }

    suspend fun create(workspaceId: String, url: String, title: String = ""): BrowserTab {
        val tab = BrowserTab(workspaceId = workspaceId, url = url, title = title)
        db.tabDao().insert(tab.toEntity())
        return tab
    }
    suspend fun update(tab: BrowserTab) = db.tabDao().update(tab.toEntity())
    suspend fun delete(tab: BrowserTab) = db.tabDao().delete(tab.toEntity())
    suspend fun deleteAll() = db.tabDao().deleteAll()
}
