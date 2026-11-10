package com.lightninglab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudClassifierContractTest {
    @Test fun tenGenusModelUsesCurrentWmoOrder() {
        assertEquals(listOf("Ac", "As", "Cb", "Cc", "Ci", "Cs", "Cu", "Ns", "Sc", "St"),
            CloudClassifier.labelsForOutputCount(10))
        assertFalse(CloudClassifier.usesCcsnUnitInput(10))
    }

    @Test fun ccsnElevenClassModelUsesItsPublishedAlphabeticalOrderAndPreprocessing() {
        assertEquals(listOf("Ac", "As", "Cb", "Cc", "Ci", "Cs", "Ct", "Cu", "Ns", "Sc", "St"),
            CloudClassifier.labelsForOutputCount(11))
        assertTrue(CloudClassifier.usesCcsnUnitInput(11))
    }

    @Test fun unsupportedOutputCountsAreRejected() {
        assertEquals(null, CloudClassifier.labelsForOutputCount(9))
        assertEquals(null, CloudClassifier.labelsForOutputCount(12))
    }
}
