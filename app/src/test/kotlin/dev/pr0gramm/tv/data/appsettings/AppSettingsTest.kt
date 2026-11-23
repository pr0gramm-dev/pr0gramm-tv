package dev.pr0gramm.tv.data.appsettings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun pointerIsTheArrowUntilChangedAndStaysChanged() = runTest {
        val file = File(tmp.root, "a.preferences_pb")
        val settings = AppSettings.create(file, CoroutineScope(StandardTestDispatcher(testScheduler)))
        assertEquals(PointerLook.HAND, settings.pointer.first())
        settings.switchPointer()
        assertEquals(PointerLook.GLOW, settings.pointer.first())
        settings.switchPointer()
        assertEquals(PointerLook.HAND, settings.pointer.first())
    }

    @Test
    fun theLookHoldsForEveryServer() = runTest {
        val settings = AppSettings.global(tmp.root, CoroutineScope(StandardTestDispatcher(testScheduler)))
        settings.switchPointer()
        assertEquals(listOf("datastore/app-settings.preferences_pb"), stored())
        assertFalse(File(tmp.root, "servers").exists())
    }

    private fun stored() = tmp.root.walk().filter { it.isFile }.map { it.relativeTo(tmp.root).path }.toList()

    @Test
    fun aStoredGlowIsGlowAfterAReopenAndTheFileHoldsOnlyThePointer() = runTest {
        val file = File(tmp.root, "c.preferences_pb")
        // One DataStore per file at a time: each scope is cancelled, and its file is released when the test runs on.
        val writer = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        AppSettings.create(file, writer).switchPointer()
        writer.cancel()
        testScheduler.advanceUntilIdle()
        val keysScope = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        val keys = PreferenceDataStoreFactory.create(scope = keysScope, produceFile = { file })
            .data.first().asMap().keys.map { it.name }
        keysScope.cancel()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("pointer"), keys)
        val reader = CoroutineScope(Job() + StandardTestDispatcher(testScheduler))
        assertEquals(PointerLook.GLOW, AppSettings.create(file, reader).pointer.first())
    }

    @Test
    fun unknownStoredLookIsTheArrow() = runTest {
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            produceFile = { File(tmp.root, "b.preferences_pb") },
        )
        store.edit { it[stringPreferencesKey("pointer")] = "LASER" }
        assertEquals(PointerLook.HAND, AppSettings(store).pointer.first())
    }
}
