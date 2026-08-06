package com.msa.fightandconquer.ui.editor

import com.msa.fightandconquer.core.editor.CustomMapValidator
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Pure java.io + :core codec, so the store is host-testable like the mesh loader. */
class CustomMapStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = CustomMapStore(File(tmp.root, "maps"))

    @Test
    fun `save load list delete round trip`() {
        val store = store()
        val a = MapTemplates.starter("id-a", "Alpha", createdAt = 1_000)
        val b = MapTemplates.starter("id-b", "Beta", createdAt = 2_000)
        store.save(a)
        store.save(b)
        assertEquals(listOf("id-b", "id-a"), store.list().map { it.id }) // newest first
        assertEquals(a, store.load("id-a"))

        store.delete("id-a")
        assertNull(store.load("id-a"))
        assertEquals(listOf("id-b"), store.list().map { it.id })
    }

    @Test
    fun `a fresh store instance rereads what another wrote`() {
        store().save(MapTemplates.starter("id-c", "Gamma", createdAt = 3_000))
        assertEquals("Gamma", store().load("id-c")?.name)
    }

    @Test
    fun `an unreadable file is skipped not fatal`() {
        val store = store()
        store.save(MapTemplates.starter("id-d", "Delta", createdAt = 4_000))
        File(tmp.root, "maps/garbage.json").writeText("{not json")
        assertEquals(listOf("id-d"), store().list().map { it.id })
    }

    @Test
    fun `the starter template is playable from birth`() {
        val starter = MapTemplates.starter("id-e", "Epsilon", createdAt = 5_000)
        assertTrue(CustomMapValidator.validate(starter).isEmpty())
    }
}
