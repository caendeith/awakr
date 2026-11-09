package io.github.caendeith.awakr.ui.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundCatalogTest {
    @Test
    fun shipsEightGradientsAndFourScenes() {
        assertEquals(8, BackgroundCatalog.gradients.size)
        assertEquals(4, BackgroundCatalog.scenes.size)
        assertEquals(12, BackgroundCatalog.all.size)
    }

    @Test
    fun keysAreUnique() {
        val keys = BackgroundCatalog.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun namesAreUnique() {
        val names = BackgroundCatalog.all.map { it.nameRes }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun everyFamilyHasAtLeastOneEntry() {
        for (family in BackgroundFamily.entries) {
            assertTrue(family.name, BackgroundCatalog.of(family).isNotEmpty())
            assertTrue(BackgroundCatalog.of(family).all { it.family == family })
        }
    }

    @Test
    fun lookupByKey() {
        assertEquals("starry", BackgroundCatalog.byKey("starry")?.key)
        assertNull(BackgroundCatalog.byKey("nope"))
        assertNull(BackgroundCatalog.byKey(null))
    }

    @Test
    fun idOfBuiltinUsesPrefix() {
        val background = BackgroundCatalog.gradients.first()
        assertEquals("builtin:" + background.key, BackgroundCatalog.idOf(background))
        assertEquals(background, BackgroundCatalog.byKey(BackgroundCatalog.idOf(background).removePrefix("builtin:")))
    }
}
