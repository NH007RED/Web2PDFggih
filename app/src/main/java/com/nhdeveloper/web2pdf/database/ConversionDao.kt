package com.nhdeveloper.web2pdf.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.nhdeveloper.web2pdf.model.ConversionRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversionDao {
    @Query("SELECT * FROM conversions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ConversionRecord>>

    @Query("SELECT * FROM conversions WHERE id = :id")
    fun observeById(id: Long): Flow<ConversionRecord?>

    @Query("SELECT * FROM conversions WHERE id = :id")
    suspend fun getById(id: Long): ConversionRecord?

    @Insert
    suspend fun insert(record: ConversionRecord): Long

    @Update
    suspend fun update(record: ConversionRecord)

    @Query("DELETE FROM conversions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
