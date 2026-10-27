package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.BridgeConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The connection settings. The access token (apiKey) is encrypted on its way into the database and decrypted on its
 * way out (TokenCipher), so every caller still sees plain settings (2026-10-27). Use these three, not the raw ones.
 */
@Dao
abstract class BridgeConfigDao {
    @Query("SELECT * FROM bridge_config WHERE id = 1 LIMIT 1")
    abstract fun rawConfig(): Flow<BridgeConfig?>

    @Query("SELECT * FROM bridge_config WHERE id = 1 LIMIT 1")
    abstract suspend fun rawConfigSync(): BridgeConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun rawSave(config: BridgeConfig)

    fun getConfig(): Flow<BridgeConfig?> = rawConfig().map { it?.copy(apiKey = TokenCipher.open(it.apiKey)) }

    suspend fun getConfigSync(): BridgeConfig? {
        val stored = rawConfigSync() ?: return null
        if (stored.apiKey.isNotEmpty() && !TokenCipher.isSealed(stored.apiKey)) {
            rawSave(stored.copy(apiKey = TokenCipher.seal(stored.apiKey)))      // a token saved before encryption
        }
        return stored.copy(apiKey = TokenCipher.open(stored.apiKey))
    }

    suspend fun saveConfig(config: BridgeConfig) = rawSave(config.copy(apiKey = TokenCipher.seal(config.apiKey)))
}
