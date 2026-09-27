package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.BridgeConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface BridgeConfigDao {
    @Query("SELECT * FROM bridge_config WHERE id = 1 LIMIT 1")
    fun getConfig(): Flow<BridgeConfig?>

    @Query("SELECT * FROM bridge_config WHERE id = 1 LIMIT 1")
    suspend fun getConfigSync(): BridgeConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: BridgeConfig)
}
