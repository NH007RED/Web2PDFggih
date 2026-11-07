package com.nhdeveloper.web2pdf.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhdeveloper.web2pdf.AppContainer
import com.nhdeveloper.web2pdf.model.ConversionRecord
import com.nhdeveloper.web2pdf.model.FileStat
import com.nhdeveloper.web2pdf.model.RecordState
import com.nhdeveloper.web2pdf.model.SortOrder
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.File

class HistoryViewModel(private val c: AppContainer) : ViewModel() {

    val query = MutableStateFlow("")
    val sort = MutableStateFlow(SortOrder.NEWEST)

    val items: StateFlow<List<ConversionRecord>> =
        combine(c.historyRepository.records, query, sort) { all, q, order ->
            val term = q.trim()
            val filtered = if (term.isEmpty()) all else all.filter {
                it.title.contains(term, ignoreCase = true) ||
                    it.url.contains(term, ignoreCase = true) ||
                    it.fileName.contains(term, ignoreCase = true)
            }
            if (order == SortOrder.NEWEST) filtered.sortedByDescending { it.createdAt }
            else filtered.sortedBy { it.createdAt }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val totalCount: StateFlow<Int> =
        combine(c.historyRepository.records, query) { all, _ -> all.size }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun recordState(id: Long): Flow<RecordState> = c.historyRepository.recordState(id)

    suspend fun stat(record: ConversionRecord): FileStat = c.fileStorage.stat(record)

    /** Copies the PDF to cache for sharing/opening/viewing. Null when the file is missing. */
    suspend fun prepareFile(record: ConversionRecord, folder: String): File? =
        c.fileStorage.copyToCache(record, folder, record.fileName)

    suspend fun exportTo(record: ConversionRecord, dest: Uri): Boolean = c.fileStorage.exportTo(record, dest)

    /** Returns true on success. */
    suspend fun remove(record: ConversionRecord, deleteFile: Boolean): Boolean =
        try {
            c.historyRepository.remove(record, deleteFile)
        } catch (e: Exception) {
            false
        }

    /** Returns null on success or an error message. */
    suspend fun rename(record: ConversionRecord, newName: String): String? =
        try {
            c.historyRepository.rename(record, newName)
            null
        } catch (e: Exception) {
            e.message ?: "Could not rename the file."
        }
}
