package com.mistysteps.app.eraser

import kotlin.test.Test
import kotlin.test.assertEquals

class EraserAccessTest {
    @Test
    fun noStoreMeansNotAvailableEvenIfOwned() {
        assertEquals(EraserState.NotAvailable, resolveEraserState(false, true, 0.0))
    }

    @Test
    fun ownedMeansUnlockedWhateverTheTasteUsed() {
        assertEquals(EraserState.Unlocked, resolveEraserState(true, true, 999.0))
    }

    @Test
    fun aPartlyUsedTasteReportsWhatIsLeft() {
        assertEquals(EraserState.Taste(70.0), resolveEraserState(true, false, 30.0))
    }

    @Test
    fun aUsedUpTasteMeansLocked() {
        assertEquals(EraserState.Locked, resolveEraserState(true, false, 100.0))
        assertEquals(EraserState.Locked, resolveEraserState(true, false, 150.0))
    }
}
