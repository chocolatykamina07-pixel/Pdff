package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import com.example.camera.EdgeDetector
import com.example.data.local.DocumentDao
import com.example.data.local.DocumentEntity
import com.example.data.local.FolderDao
import com.example.data.local.FolderEntity
import com.example.data.local.PageDao
import com.example.data.local.PageEntity
import com.example.data.local.SignatureDao
import com.example.data.local.SignatureEntity
import com.example.domain.model.CropQuad
import com.example.domain.model.FilterType
import com.example.processing.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanNestRepository(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val pageDao: PageDao,
    private val folderDao: FolderDao,
    private val signatureDao: SignatureDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    val activeDocuments: Flow<List<DocumentEntity>> = documentDao.getActiveDocuments()
    val trashedDocuments: Flow<List<DocumentEntity>> = documentDao.getTrashedDocuments()
    val trashedCount: Flow<Int> = documentDao.getTrashedCount()
    val allFolders: Flow<List<FolderEntity>> = folderDao.getAllFolders()
    val allSignatures: Flow<List<SignatureEntity>> = signatureDao.getAllSignatures()

    fun observeDocument(documentId: Long): Flow<DocumentEntity?> = documentDao.observeDocumentById(documentId)
    fun observePages(documentId: Long): Flow<List<PageEntity>> = pageDao.observePagesForDocument(documentId)

    suspend fun getDocument(documentId: Long): DocumentEntity? = documentDao.getDocumentById(documentId)
    suspend fun getPages(documentId: Long): List<PageEntity> = pageDao.getPagesForDocument(documentId)
    suspend fun getPage(pageId: Long): PageEntity? = pageDao.getPageById(pageId)

    private fun scansDir(documentId: Long): File =
        File(context.filesDir, "scans/doc_$documentId").apply { mkdirs() }

    private fun signaturesDir(): File =
        File(context.filesDir, "signatures").apply { mkdirs() }

    fun exportsCacheDir(): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

    fun defaultDocumentName(prefix: String = "Scan"): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.getDefault())
        return "$prefix ${fmt.format(Date())}"
    }

    suspend fun createDocumentFromBitmaps(
        name: String,
        bitmaps: List<Bitmap>,
        folderId: Long? = null,
        filterType: FilterType = FilterType.AUTO_ENHANCE,
        autoDetectCrop: Boolean = true
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val docId = documentDao.insertDocument(
            DocumentEntity(
                name = name,
                createdAt = now,
                updatedAt = now,
                folderId = folderId
            )
        )
        val dir = scansDir(docId)
        val pages = mutableListOf<PageEntity>()
        bitmaps.forEachIndexed { idx, bmp ->
            val rawFile = File(dir, "raw_${now}_$idx.jpg")
            val procFile = File(dir, "proc_${now}_$idx.jpg")
            ImageProcessor.saveBitmapJpeg(bmp, rawFile, 95)

            val quad = if (autoDetectCrop) EdgeDetector.detectEdges(bmp) else CropQuad.FULL
            val cropped = ImageProcessor.applyPerspectiveCrop(bmp, quad)
            val filtered = ImageProcessor.applyFilterAndAdjustments(cropped, filterType)
            if (cropped !== bmp) cropped.recycle()
            ImageProcessor.saveBitmapJpeg(filtered, procFile, 92)
            filtered.recycle()

            pages.add(
                PageEntity(
                    documentId = docId,
                    pageIndex = idx,
                    originalImagePath = rawFile.absolutePath,
                    processedImagePath = procFile.absolutePath,
                    filter = filterType.name,
                    cropPointsJson = json.encodeToString(quad)
                )
            )
        }
        pageDao.insertPages(pages)
        refreshDocumentStats(docId)
        docId
    }

    suspend fun createDocumentFromImageFiles(
        name: String,
        sourceFiles: List<File>,
        folderId: Long? = null,
        filterType: FilterType = FilterType.AUTO_ENHANCE,
        autoDetectCrop: Boolean = true
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val docId = documentDao.insertDocument(
            DocumentEntity(name = name, createdAt = now, updatedAt = now, folderId = folderId)
        )
        val dir = scansDir(docId)
        val pages = mutableListOf<PageEntity>()
        sourceFiles.forEachIndexed { idx, srcFile ->
            val bmp = ImageProcessor.decodeSampledBitmap(srcFile.absolutePath, 2400) ?: return@forEachIndexed
            val rawFile = File(dir, "raw_${now}_$idx.jpg")
            val procFile = File(dir, "proc_${now}_$idx.jpg")
            ImageProcessor.saveBitmapJpeg(bmp, rawFile, 95)

            val quad = if (autoDetectCrop) EdgeDetector.detectEdges(bmp) else CropQuad.FULL
            val cropped = ImageProcessor.applyPerspectiveCrop(bmp, quad)
            val filtered = ImageProcessor.applyFilterAndAdjustments(cropped, filterType)
            if (cropped !== bmp) cropped.recycle()
            ImageProcessor.saveBitmapJpeg(filtered, procFile, 92)
            filtered.recycle()
            bmp.recycle()

            pages.add(
                PageEntity(
                    documentId = docId,
                    pageIndex = idx,
                    originalImagePath = rawFile.absolutePath,
                    processedImagePath = procFile.absolutePath,
                    filter = filterType.name,
                    cropPointsJson = json.encodeToString(quad)
                )
            )
        }
        pageDao.insertPages(pages)
        refreshDocumentStats(docId)
        docId
    }

    suspend fun appendImageFilesToDocument(
        documentId: Long,
        sourceFiles: List<File>,
        filterType: FilterType = FilterType.AUTO_ENHANCE
    ) = withContext(Dispatchers.IO) {
        val existing = pageDao.getPagesForDocument(documentId)
        val startIndex = existing.size
        val now = System.currentTimeMillis()
        val dir = scansDir(documentId)
        val newPages = mutableListOf<PageEntity>()
        sourceFiles.forEachIndexed { idx, srcFile ->
            val bmp = ImageProcessor.decodeSampledBitmap(srcFile.absolutePath, 2400) ?: return@forEachIndexed
            val rawFile = File(dir, "raw_${now}_${startIndex + idx}.jpg")
            val procFile = File(dir, "proc_${now}_${startIndex + idx}.jpg")
            ImageProcessor.saveBitmapJpeg(bmp, rawFile, 95)
            val quad = EdgeDetector.detectEdges(bmp)
            val cropped = ImageProcessor.applyPerspectiveCrop(bmp, quad)
            val filtered = ImageProcessor.applyFilterAndAdjustments(cropped, filterType)
            if (cropped !== bmp) cropped.recycle()
            ImageProcessor.saveBitmapJpeg(filtered, procFile, 92)
            filtered.recycle()
            bmp.recycle()
            newPages.add(
                PageEntity(
                    documentId = documentId,
                    pageIndex = startIndex + idx,
                    originalImagePath = rawFile.absolutePath,
                    processedImagePath = procFile.absolutePath,
                    filter = filterType.name,
                    cropPointsJson = json.encodeToString(quad)
                )
            )
        }
        pageDao.insertPages(newPages)
        refreshDocumentStats(documentId)
    }

    suspend fun importUrisAsDocument(
        name: String,
        uris: List<Uri>,
        folderId: Long? = null,
        filterType: FilterType = FilterType.ORIGINAL
    ): Long? = withContext(Dispatchers.IO) {
        val tempFiles = mutableListOf<File>()
        try {
            uris.forEachIndexed { idx, uri ->
                val bmp = ImageProcessor.decodeUriSampled(context, uri, 2200) ?: return@forEachIndexed
                val tmp = File(context.cacheDir, "imp_${System.currentTimeMillis()}_$idx.jpg")
                ImageProcessor.saveBitmapJpeg(bmp, tmp, 94)
                bmp.recycle()
                tempFiles.add(tmp)
            }
            if (tempFiles.isEmpty()) return@withContext null
            createDocumentFromImageFiles(
                name = name,
                sourceFiles = tempFiles,
                folderId = folderId,
                filterType = filterType,
                autoDetectCrop = false
            )
        } finally {
            tempFiles.forEach { it.delete() }
        }
    }

    suspend fun updatePageCropAndTransform(
        pageId: Long,
        quad: CropQuad,
        rotationDegrees: Int,
        flipHorizontal: Boolean
    ) = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext
        val rawBmp = ImageProcessor.decodeSampledBitmap(page.originalImagePath, 2400) ?: return@withContext
        val cropped = ImageProcessor.applyPerspectiveCrop(rawBmp, quad, rotationDegrees, flipHorizontal)
        rawBmp.recycle()

        val filter = runCatching { FilterType.valueOf(page.filter) }.getOrDefault(FilterType.AUTO_ENHANCE)
        val processed = ImageProcessor.applyFilterAndAdjustments(
            cropped,
            filter = filter,
            brightness = page.brightness,
            contrast = page.contrast,
            sharpness = page.sharpness,
            removeShadows = page.removeShadows,
            removeBlemishes = page.removeBlemishes
        )
        if (cropped !== processed) cropped.recycle()

        val outFile = File(scansDir(page.documentId), "proc_${page.id}_${System.currentTimeMillis()}.jpg")
        ImageProcessor.saveBitmapJpeg(processed, outFile, 92)
        processed.recycle()

        val oldProc = File(page.processedImagePath)
        if (oldProc.exists() && oldProc.absolutePath != page.originalImagePath) oldProc.delete()

        pageDao.updatePage(
            page.copy(
                processedImagePath = outFile.absolutePath,
                rotation = ((rotationDegrees % 360) + 360) % 360,
                flipHorizontal = flipHorizontal,
                cropPointsJson = json.encodeToString(quad)
            )
        )
        refreshDocumentStats(page.documentId)
    }

    suspend fun updatePageFilterSettings(
        pageId: Long,
        filterType: FilterType,
        brightness: Float,
        contrast: Float,
        sharpness: Float,
        removeShadows: Boolean,
        removeBlemishes: Boolean,
        applyToAllPages: Boolean
    ) = withContext(Dispatchers.IO) {
        val target = pageDao.getPageById(pageId) ?: return@withContext
        val pages = if (applyToAllPages) pageDao.getPagesForDocument(target.documentId) else listOf(target)
        for (page in pages) {
            val rawBmp = ImageProcessor.decodeSampledBitmap(page.originalImagePath, 2400) ?: continue
            val quad = decodeCropQuad(page.cropPointsJson)
            val cropped = ImageProcessor.applyPerspectiveCrop(rawBmp, quad, page.rotation, page.flipHorizontal)
            rawBmp.recycle()

            val processed = ImageProcessor.applyFilterAndAdjustments(
                cropped,
                filter = filterType,
                brightness = brightness,
                contrast = contrast,
                sharpness = sharpness,
                removeShadows = removeShadows,
                removeBlemishes = removeBlemishes
            )
            if (cropped !== processed) cropped.recycle()

            val outFile = File(scansDir(page.documentId), "proc_${page.id}_${System.currentTimeMillis()}.jpg")
            ImageProcessor.saveBitmapJpeg(processed, outFile, 92)
            processed.recycle()

            val oldProc = File(page.processedImagePath)
            if (oldProc.exists() && oldProc.absolutePath != page.originalImagePath) oldProc.delete()

            pageDao.updatePage(
                page.copy(
                    processedImagePath = outFile.absolutePath,
                    filter = filterType.name,
                    brightness = brightness,
                    contrast = contrast,
                    sharpness = sharpness,
                    removeShadows = removeShadows,
                    removeBlemishes = removeBlemishes
                )
            )
        }
        refreshDocumentStats(target.documentId)
    }

    suspend fun rotatePageQuick(pageId: Long, deltaDegrees: Int = 90) = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext
        val newRot = ((page.rotation + deltaDegrees) % 360 + 360) % 360
        val quad = decodeCropQuad(page.cropPointsJson)
        updatePageCropAndTransform(pageId, quad, newRot, page.flipHorizontal)
    }

    suspend fun saveFlattenedPageBitmap(pageId: Long, flattenedBitmap: Bitmap) = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext
        val outFile = File(scansDir(page.documentId), "proc_${page.id}_${System.currentTimeMillis()}.jpg")
        ImageProcessor.saveBitmapJpeg(flattenedBitmap, outFile, 94)
        val oldProc = File(page.processedImagePath)
        if (oldProc.exists() && oldProc.absolutePath != page.originalImagePath) oldProc.delete()
        pageDao.updatePage(page.copy(processedImagePath = outFile.absolutePath))
        refreshDocumentStats(page.documentId)
    }

    suspend fun applyWatermarkToDocument(documentId: Long, watermarkText: String) = withContext(Dispatchers.IO) {
        val pages = pageDao.getPagesForDocument(documentId)
        for (page in pages) {
            val bmp = ImageProcessor.decodeSampledBitmap(page.processedImagePath, 2200) ?: continue
            val marked = ImageProcessor.applyWatermark(bmp, watermarkText)
            bmp.recycle()
            val outFile = File(scansDir(documentId), "wm_${page.id}_${System.currentTimeMillis()}.jpg")
            ImageProcessor.saveBitmapJpeg(marked, outFile, 92)
            marked.recycle()
            val oldProc = File(page.processedImagePath)
            if (oldProc.exists() && oldProc.absolutePath != page.originalImagePath) oldProc.delete()
            pageDao.updatePage(page.copy(processedImagePath = outFile.absolutePath))
        }
        refreshDocumentStats(documentId)
    }

    suspend fun reorderPages(documentId: Long, orderedPageIds: List<Long>) = withContext(Dispatchers.IO) {
        val pages = pageDao.getPagesForDocument(documentId).associateBy { it.id }
        val updated = orderedPageIds.mapIndexedNotNull { idx, id ->
            pages[id]?.copy(pageIndex = idx)
        }
        pageDao.updatePages(updated)
        refreshDocumentStats(documentId)
    }

    suspend fun deletePage(pageId: Long) = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext
        val docId = page.documentId
        File(page.originalImagePath).delete()
        File(page.processedImagePath).delete()
        pageDao.deletePageById(pageId)
        val remaining = pageDao.getPagesForDocument(docId)
        if (remaining.isEmpty()) {
            deleteDocumentPermanently(docId)
        } else {
            val reindexed = remaining.mapIndexed { idx, p -> p.copy(pageIndex = idx) }
            pageDao.updatePages(reindexed)
            refreshDocumentStats(docId)
        }
    }

    suspend fun updateOcrText(documentId: Long, pageId: Long?, text: String) = withContext(Dispatchers.IO) {
        if (pageId != null) {
            pageDao.getPageById(pageId)?.let { page ->
                pageDao.updatePage(page.copy(ocrText = text))
            }
            val allPages = pageDao.getPagesForDocument(documentId)
            val combined = allPages.mapNotNull { it.ocrText?.takeIf { t -> t.isNotBlank() } }.joinToString("\n\n")
            documentDao.getDocumentById(documentId)?.let { doc ->
                documentDao.updateDocument(doc.copy(ocrText = combined.ifBlank { text }, updatedAt = System.currentTimeMillis()))
            }
        } else {
            documentDao.getDocumentById(documentId)?.let { doc ->
                documentDao.updateDocument(doc.copy(ocrText = text, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    suspend fun renameDocument(documentId: Long, newName: String) {
        val doc = documentDao.getDocumentById(documentId) ?: return
        documentDao.updateDocument(doc.copy(name = newName.trim().ifBlank { doc.name }, updatedAt = System.currentTimeMillis()))
    }

    suspend fun toggleFavorite(documentId: Long) {
        val doc = documentDao.getDocumentById(documentId) ?: return
        documentDao.updateDocument(doc.copy(isFavorite = !doc.isFavorite))
    }

    suspend fun toggleLocked(documentId: Long) {
        val doc = documentDao.getDocumentById(documentId) ?: return
        documentDao.updateDocument(doc.copy(isLocked = !doc.isLocked))
    }

    suspend fun moveToFolder(documentIds: List<Long>, folderId: Long?) {
        for (id in documentIds) {
            documentDao.getDocumentById(id)?.let { doc ->
                documentDao.updateDocument(doc.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    suspend fun duplicateDocument(documentId: Long): Long? = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentById(documentId) ?: return@withContext null
        val pages = pageDao.getPagesForDocument(documentId)
        val now = System.currentTimeMillis()
        val newDocId = documentDao.insertDocument(
            doc.copy(
                id = 0,
                name = "${doc.name} (Copy)",
                createdAt = now,
                updatedAt = now,
                isFavorite = false
            )
        )
        val destDir = scansDir(newDocId)
        val copiedPages = pages.mapIndexed { idx, p ->
            val newRaw = File(destDir, "raw_${now}_$idx.jpg")
            val newProc = File(destDir, "proc_${now}_$idx.jpg")
            File(p.originalImagePath).takeIf { it.exists() }?.copyTo(newRaw, overwrite = true)
            File(p.processedImagePath).takeIf { it.exists() }?.copyTo(newProc, overwrite = true)
            p.copy(
                id = 0,
                documentId = newDocId,
                originalImagePath = newRaw.absolutePath,
                processedImagePath = newProc.absolutePath
            )
        }
        pageDao.insertPages(copiedPages)
        refreshDocumentStats(newDocId)
        newDocId
    }

    suspend fun mergeDocuments(documentIds: List<Long>, mergedName: String): Long? = withContext(Dispatchers.IO) {
        if (documentIds.size < 2) return@withContext null
        val now = System.currentTimeMillis()
        val newDocId = documentDao.insertDocument(
            DocumentEntity(name = mergedName, createdAt = now, updatedAt = now)
        )
        val destDir = scansDir(newDocId)
        val mergedPages = mutableListOf<PageEntity>()
        var pageCounter = 0
        for (docId in documentIds) {
            val pages = pageDao.getPagesForDocument(docId)
            for (p in pages) {
                val newRaw = File(destDir, "raw_${now}_$pageCounter.jpg")
                val newProc = File(destDir, "proc_${now}_$pageCounter.jpg")
                File(p.originalImagePath).takeIf { it.exists() }?.copyTo(newRaw, overwrite = true)
                File(p.processedImagePath).takeIf { it.exists() }?.copyTo(newProc, overwrite = true)
                mergedPages.add(
                    p.copy(
                        id = 0,
                        documentId = newDocId,
                        pageIndex = pageCounter++,
                        originalImagePath = newRaw.absolutePath,
                        processedImagePath = newProc.absolutePath
                    )
                )
            }
        }
        pageDao.insertPages(mergedPages)
        refreshDocumentStats(newDocId)
        newDocId
    }

    suspend fun moveToTrash(documentIds: List<Long>) {
        val now = System.currentTimeMillis()
        for (id in documentIds) {
            documentDao.getDocumentById(id)?.let { doc ->
                documentDao.updateDocument(doc.copy(isTrashed = true, trashedAt = now))
            }
        }
    }

    suspend fun restoreFromTrash(documentIds: List<Long>) {
        for (id in documentIds) {
            documentDao.getDocumentById(id)?.let { doc ->
                documentDao.updateDocument(doc.copy(isTrashed = false, trashedAt = null))
            }
        }
    }

    suspend fun deleteDocumentPermanently(documentId: Long) = withContext(Dispatchers.IO) {
        scansDir(documentId).deleteRecursively()
        documentDao.deleteDocumentById(documentId)
    }

    suspend fun purgeExpiredTrash() = withContext(Dispatchers.IO) {
        val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L
        val cutoff = System.currentTimeMillis() - thirtyDaysMs
        val expired = documentDao.getExpiredTrashedDocuments(cutoff)
        expired.forEach { scansDir(it.id).deleteRecursively() }
        documentDao.purgeOldTrashedDocuments(cutoff)
    }

    suspend fun createFolder(name: String): Long {
        return folderDao.insertFolder(FolderEntity(name = name.trim()))
    }

    suspend fun deleteFolder(folderId: Long) {
        folderDao.deleteFolderById(folderId)
    }

    suspend fun saveSignature(name: String, bitmap: Bitmap): Long = withContext(Dispatchers.IO) {
        val file = File(signaturesDir(), "sig_${System.currentTimeMillis()}.png")
        ImageProcessor.saveBitmapPng(bitmap, file)
        signatureDao.insertSignature(
            SignatureEntity(name = name, pngPath = file.absolutePath)
        )
    }

    suspend fun deleteSignature(signature: SignatureEntity) = withContext(Dispatchers.IO) {
        File(signature.pngPath).delete()
        signatureDao.deleteSignatureById(signature.id)
    }

    suspend fun refreshDocumentStats(documentId: Long) = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentById(documentId) ?: return@withContext
        val pages = pageDao.getPagesForDocument(documentId)
        val totalSize = pages.sumOf { File(it.processedImagePath).length() }
        val thumb = pages.firstOrNull()?.processedImagePath
        documentDao.updateDocument(
            doc.copy(
                pageCount = pages.size,
                sizeBytes = totalSize,
                thumbnailPath = thumb,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun decodeCropQuad(jsonStr: String): CropQuad {
        if (jsonStr.isBlank()) return CropQuad()
        return runCatching { json.decodeFromString<CropQuad>(jsonStr) }.getOrDefault(CropQuad())
    }

    fun getCacheSizeBytes(): Long {
        return calculateDirSize(context.cacheDir)
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }

    suspend fun backupFileToFolderUri(treeUriString: String, file: File, mimeType: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val treeUri = Uri.parse(treeUriString)
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val destUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    dirUri,
                    mimeType,
                    file.name
                ) ?: return@runCatching false
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                true
            }.getOrDefault(false)
        }
}
