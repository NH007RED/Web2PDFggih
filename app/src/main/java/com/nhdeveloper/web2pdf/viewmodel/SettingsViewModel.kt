package com.nhdeveloper.web2pdf.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nhdeveloper.web2pdf.AppContainer
import com.nhdeveloper.web2pdf.model.AppSettings
import com.nhdeveloper.web2pdf.model.PdfOptions
import com.nhdeveloper.web2pdf.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val c: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = c.settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** (number of saved PDFs, total bytes) */
    val stats: StateFlow<Pair<Int, Long>> = c.historyRepository.records
        .map { list -> list.size to list.sumOf { it.sizeBytes } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0 to 0L)

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { c.settingsStore.setTheme(mode) }
    }

    fun setDefaults(options: PdfOptions) {
        viewModelScope.launch { c.settingsStore.setDefaults(options) }
    }

    /** Returns true when the folder was accepted. */
    suspend fun setSaveFolder(uri: Uri): Boolean {
        if (!c.fileStorage.takeTree(uri)) return false
        settings.value.saveTreeUri?.let { if (it != uri.toString()) c.fileStorage.releaseTree(it) }
        c.settingsStore.setSaveTree(uri.toString())
        return true
    }

    fun resetSaveFolder() {
        viewModelScope.launch {
            settings.value.saveTreeUri?.let { c.fileStorage.releaseTree(it) }
            c.settingsStore.setSaveTree(null)
        }
    }

    fun folderAvailable(uri: String): Boolean = c.fileStorage.treeAvailable(uri)

    suspend fun cacheSize(): Long = c.fileStorage.cacheSize()

    suspend fun clearCache(): Long = c.fileStorage.clearCache()
}
