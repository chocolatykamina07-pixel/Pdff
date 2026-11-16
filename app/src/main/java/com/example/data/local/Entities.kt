package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders"
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("folderId")]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val pageCount: Int = 0,
    val sizeBytes: Long = 0L,
    val folderId: Long? = null,
    val isFavorite: Boolean = false,
    val isLocked: Boolean = false,
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null,
    val ocrText: String? = null,
    val thumbnailPath: String? = null,
    val tags: String = ""
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val originalImagePath: String,
    val processedImagePath: String,
    val filter: String = "AUTO_ENHANCE",
    val brightness: Float = 0f,
    val contrast: Float = 1.0f,
    val sharpness: Float = 0.2f,
    val removeShadows: Boolean = false,
    val removeBlemishes: Boolean = false,
    val rotation: Int = 0,
    val flipHorizontal: Boolean = false,
    val cropPointsJson: String = "",
    val ocrText: String? = null
)

@Entity(tableName = "signatures")
data class SignatureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val pngPath: String,
    val createdAt: Long = System.currentTimeMillis()
)
