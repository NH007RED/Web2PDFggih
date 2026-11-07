package com.nhdeveloper.web2pdf.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PaperSize(val label: String) { A4("A4"), A3("A3"), LETTER("Letter") }

enum class Orientation(val label: String) { PORTRAIT("Portrait"), LANDSCAPE("Landscape") }

enum class MarginSize(val label: String, val mils: Int) {
    NONE("None", 0),
    NARROW("Narrow", 250),
    NORMAL("Normal", 500),
    WIDE("Wide", 1000)
}

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class SortOrder { NEWEST, OLDEST }

data class PdfOptions(
    val paper: PaperSize = PaperSize.A4,
    val orientation: Orientation = Orientation.PORTRAIT,
    val margin: MarginSize = MarginSize.NORMAL,
    val backgroundGraphics: Boolean = true,
    val pageRange: String = ""
)

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val defaultOptions: PdfOptions = PdfOptions(),
    val saveTreeUri: String? = null
)

@Entity(tableName = "conversions")
data class ConversionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val fileName: String,
    val createdAt: Long,
    val fileUri: String,
    val filePath: String?,
    val sizeBytes: Long,
    val pageCount: Int
)

data class FileStat(val exists: Boolean, val size: Long)

sealed interface ConversionState {
    data object Idle : ConversionState
    data class Running(val status: String, val percent: Int = 0) : ConversionState
    data class Success(val record: ConversionRecord) : ConversionState
    data class Failed(val message: String, val canUseSystemPrint: Boolean = false) : ConversionState
}

sealed interface RecordState {
    data object Loading : RecordState
    data object NotFound : RecordState
    data class Found(val record: ConversionRecord) : RecordState
}
