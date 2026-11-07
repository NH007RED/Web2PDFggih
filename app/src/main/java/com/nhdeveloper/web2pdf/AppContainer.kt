package com.nhdeveloper.web2pdf

import android.app.Application
import android.content.Context
import com.nhdeveloper.web2pdf.data.FileStorage
import com.nhdeveloper.web2pdf.data.SettingsStore
import com.nhdeveloper.web2pdf.database.AppDatabase
import com.nhdeveloper.web2pdf.pdf.PdfGenerator
import com.nhdeveloper.web2pdf.repository.HistoryRepository

class AppContainer(context: Context) {
    private val database = AppDatabase.create(context)
    val settingsStore = SettingsStore(context.applicationContext)
    val fileStorage = FileStorage(context.applicationContext)
    val pdfGenerator = PdfGenerator(context.applicationContext)
    val historyRepository = HistoryRepository(database.conversionDao(), fileStorage)
}

class Web2PdfApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
