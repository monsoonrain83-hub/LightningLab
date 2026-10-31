package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudTaxonomyTest {
    @Test fun tenNaturalGeneraArePresent() {
        val natural = CloudInfo.genusCodes.filter { it != "Ct" }
        assertEquals(10, natural.size)
        assertTrue(natural.containsAll(listOf("Ci", "Cc", "Cs", "Ac", "As", "Ns", "Sc", "St", "Cu", "Cb")))
    }

    @Test fun speciesAreSeparatedFromVarieties() {
        assertTrue("castellanus" in CloudInfo.speciesByGenus.getValue("Ac"))
        assertTrue("undulatus" in CloudInfo.varieties)
        assertFalse("undulatus" in CloudInfo.speciesByGenus.values.flatten())
    }

    @Test fun supplementaryFeaturesIncludeImportantConvectiveStructures() {
        assertTrue(CloudInfo.supplementary.containsAll(listOf("arcus", "asperitas", "virga", "incus", "mamma", "pileus", "tuba")))
        assertEquals("Mamma (Mammatus)", CloudInfo.displayFeature("mamma"))
    }
}
