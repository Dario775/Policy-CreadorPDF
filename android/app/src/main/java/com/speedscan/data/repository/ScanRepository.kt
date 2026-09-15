package com.speedscan.data.repository

import com.speedscan.core.domain.model.Document
import com.speedscan.data.local.DocumentDao
import com.speedscan.data.local.DocumentEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ScanRepository @Inject constructor(
    private val documentDao: DocumentDao
) {
    val documents: Flow<List<Document>> = documentDao.getAll().map { entities ->
        entities.map { entity ->
            Document(entity.id, entity.name, entity.createdAt, entity.thumbnailPath, entity.pageCount)
        }
    }

    suspend fun createDocument(name: String, thumbPath: String) {
        documentDao.insert(DocumentEntity(
            name = name,
            createdAt = System.currentTimeMillis(),
            thumbnailPath = thumbPath,
            pageCount = 0
        ))
    }
}
