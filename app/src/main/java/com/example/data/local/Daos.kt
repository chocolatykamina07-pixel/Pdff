package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: Long): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderById(id: Long)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE isTrashed = 0 ORDER BY updatedAt DESC")
    fun getActiveDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    fun getTrashedDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT COUNT(*) FROM documents WHERE isTrashed = 1")
    fun getTrashedCount(): Flow<Int>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeDocumentById(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE folderId = :folderId AND isTrashed = 0")
    suspend fun getDocumentsInFolder(folderId: Long): List<DocumentEntity>

    @Query("SELECT COUNT(*) FROM documents WHERE folderId = :folderId AND isTrashed = 0")
    fun observeFolderItemCount(folderId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: DocumentEntity): Long

    @Update
    suspend fun updateDocument(doc: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Query("DELETE FROM documents WHERE isTrashed = 1 AND trashedAt IS NOT NULL AND trashedAt < :cutoffTimestamp")
    suspend fun purgeOldTrashedDocuments(cutoffTimestamp: Long)

    @Query("SELECT * FROM documents WHERE isTrashed = 1 AND trashedAt IS NOT NULL AND trashedAt < :cutoffTimestamp")
    suspend fun getExpiredTrashedDocuments(cutoffTimestamp: Long): List<DocumentEntity>
}

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    fun observePagesForDocument(documentId: Long): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun getPagesForDocument(documentId: Long): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :pageId")
    suspend fun getPageById(pageId: Long): PageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: PageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<PageEntity>): List<Long>

    @Update
    suspend fun updatePage(page: PageEntity)

    @Update
    suspend fun updatePages(pages: List<PageEntity>)

    @Query("DELETE FROM pages WHERE id = :pageId")
    suspend fun deletePageById(pageId: Long)
}

@Dao
interface SignatureDao {
    @Query("SELECT * FROM signatures ORDER BY createdAt DESC")
    fun getAllSignatures(): Flow<List<SignatureEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignature(signature: SignatureEntity): Long

    @Query("DELETE FROM signatures WHERE id = :id")
    suspend fun deleteSignatureById(id: Long)
}
