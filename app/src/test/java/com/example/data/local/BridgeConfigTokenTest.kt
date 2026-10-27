package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.BridgeConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The access token is encrypted on its way into the database (TokenCipher, 2026-10-27). Callers must still see it
 * as typed, and a token saved before this change must keep working. (Robolectric has no Android Keystore, so here
 * TokenCipher keeps the token as is: these tests check that the token is never lost.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BridgeConfigTokenTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun theTokenComesBackAsTyped() = runBlocking {
        val dao = db.bridgeConfigDao()
        dao.saveConfig(BridgeConfig(apiKey = "hud-token-123"))
        assertEquals("hud-token-123", dao.getConfigSync()?.apiKey)
        assertEquals("hud-token-123", dao.getConfig().first()?.apiKey)
    }

    @Test
    fun aTokenSavedBeforeEncryptionStillWorks() = runBlocking {
        val dao = db.bridgeConfigDao()
        dao.rawSave(BridgeConfig(apiKey = "old-plain-token"))       // as builds before 2026-10-27 stored it
        assertEquals("old-plain-token", dao.getConfigSync()?.apiKey)
    }

    @Test
    fun theOldTailscaleAddressMovesToHttpsAndTypedOnesStay() {
        assertEquals(BridgeConfig.TAILNET_HTTPS, BridgeConfig.upgradeRemote("http://100.109.85.92:8080"))
        assertEquals(BridgeConfig.TAILNET_HTTPS, BridgeConfig.upgradeRemote("http://100.109.85.92:8080/"))
        assertEquals("http://10.0.0.5:8080", BridgeConfig.upgradeRemote("http://10.0.0.5:8080"))
        assertEquals(BridgeConfig.TAILNET_HTTPS, BridgeConfig().remoteUrl)
    }

    @Test
    fun anUnreadableTokenIsEmptyNotGarbage() {
        // The key is gone (a new phone, a reset): the user is asked for the token again instead of sending junk
        assertEquals("", TokenCipher.open("enc1:not-really-encrypted"))
        assertEquals("plain", TokenCipher.open("plain"))
        assertEquals("", TokenCipher.seal(""))
    }
}
