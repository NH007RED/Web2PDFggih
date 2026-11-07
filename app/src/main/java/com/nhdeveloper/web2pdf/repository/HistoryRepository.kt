package com.nhdeveloper.web2pdf.repository

import com.nhdeveloper.web2pdf.data.FileStorage
import com.nhdeveloper.web2pdf.database.ConversionDao
import com.nhdeveloper.web2pdf.model.ConversionRecord
import com.nhdeveloper.web2pdf.model.RecordState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class HistoryRepository(
    private val dao: ConversionDao,
    private val storage: FileStorage
) {
    val records: Flow<List<ConversionRecord>> = dao.observeAll()

    fun recordState(id: Long): Flow<RecordState> =
        dao.observeById(id)
            .map<ConversionRecord?, RecordState> { if (it == null) RecordState.NotFound else RecordState.Found(it) }
            .onStart { emit(RecordState.Loading) }

    suspend fun get(id: Long): ConversionRecord? = dao.getById(id)

    suspend fun add(record: ConversionRecord): Long = dao.insert(record)

    /** Removes the history entry; optionally deletes the PDF too. Returns false if the PDF could not be deleted. */
    suspend fun remove(record: ConversionRecord, deleteFile: Boolean): Boolean {
        var ok = true
        if (deleteFile) ok = storage.delete(record)
        if (ok || !deleteFile) dao.deleteById(record.id)
        return ok
    }

    suspend fun rename(record: ConversionRecord, newName: String) {
        val saved = storage.rename(record, newName)
        dao.update(
            record.copy(
                fileName = saved.name,
                fileUri = saved.uri,
                filePath = saved.filePath
            )
        )
    }
}
