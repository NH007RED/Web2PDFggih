package com.nhdeveloper.web2pdf.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nhdeveloper.web2pdf.AppContainer

class ViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(ConvertViewModel::class.java) -> ConvertViewModel(container) as T
        modelClass.isAssignableFrom(HistoryViewModel::class.java) -> HistoryViewModel(container) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(container) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: " + modelClass.name)
    }
}
