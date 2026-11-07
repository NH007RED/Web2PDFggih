package com.nhdeveloper.web2pdf.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nhdeveloper.web2pdf.model.AppSettings
import com.nhdeveloper.web2pdf.model.MarginSize
import com.nhdeveloper.web2pdf.model.Orientation
import com.nhdeveloper.web2pdf.model.PaperSize
import com.nhdeveloper.web2pdf.model.PdfOptions
import com.nhdeveloper.web2pdf.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "web2pdf_settings")

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: default

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val PAPER = stringPreferencesKey("paper")
        val ORIENTATION = stringPreferencesKey("orientation")
        val MARGIN = stringPreferencesKey("margin")
        val BACKGROUND = booleanPreferencesKey("background_graphics")
        val TREE = stringPreferencesKey("save_tree_uri")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { p ->
            AppSettings(
                theme = p[Keys.THEME].toEnum(ThemeMode.SYSTEM),
                defaultOptions = PdfOptions(
                    paper = p[Keys.PAPER].toEnum(PaperSize.A4),
                    orientation = p[Keys.ORIENTATION].toEnum(Orientation.PORTRAIT),
                    margin = p[Keys.MARGIN].toEnum(MarginSize.NORMAL),
                    backgroundGraphics = p[Keys.BACKGROUND] ?: true
                ),
                saveTreeUri = p[Keys.TREE]
            )
        }

    suspend fun setTheme(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setDefaults(options: PdfOptions) {
        context.settingsDataStore.edit {
            it[Keys.PAPER] = options.paper.name
            it[Keys.ORIENTATION] = options.orientation.name
            it[Keys.MARGIN] = options.margin.name
            it[Keys.BACKGROUND] = options.backgroundGraphics
        }
    }

    suspend fun setSaveTree(uri: String?) {
        context.settingsDataStore.edit {
            if (uri == null) it.remove(Keys.TREE) else it[Keys.TREE] = uri
        }
    }
}
