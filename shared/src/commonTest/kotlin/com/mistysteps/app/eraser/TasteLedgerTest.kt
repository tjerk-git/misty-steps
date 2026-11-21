package com.mistysteps.app.eraser

import kotlin.test.Test
import kotlin.test.assertEquals

class TasteLedgerTest {
    @Test
    fun spendingAddsUpAndIsSavedEachTime() {
        val saved = ArrayList<Double>()
        val ledger = TasteLedger(0.0) { saved += it }
        ledger.spend(30.0)
        ledger.spend(40.0)
        assertEquals(70.0, ledger.usedM, 1e-9)
        assertEquals(30.0, ledger.metersLeft, 1e-9)
        assertEquals(listOf(30.0, 70.0), saved)
    }

    @Test
    fun theTasteIsNeverNegativeAndNeverGoesBelowZeroLeft() {
        val ledger = TasteLedger(90.0) {}
        ledger.spend(-5.0)
        assertEquals(90.0, ledger.usedM, 1e-9)
        ledger.spend(50.0)
        assertEquals(0.0, ledger.metersLeft, 1e-9)
    }

    @Test
    fun aLedgerStartedFromASavedAmountContinuesFromIt() {
        assertEquals(60.0, TasteLedger(40.0) {}.metersLeft, 1e-9)
    }

    @Test
    fun resetGivesTheWholeTasteBack() {
        val ledger = TasteLedger(100.0) {}
        ledger.reset()
        assertEquals(EraserRules.TASTE_M, ledger.metersLeft, 1e-9)
    }
}
