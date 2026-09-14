package com.wfsdiy.wfs_control_2

import com.wfsdiy.wfs_control_2.VisRefreshWatchdog.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Visualisation tab's pull policy: which channels it shows, and when its 2 s check
 * asks the desktop for the vis state again (/remote/vis/request), or falls back to one
 * full re-dump.
 */
class VisRefreshWatchdogTest {

    private var revision = 0L

    private fun row(receivedAtMs: Long, hasDelays: Boolean = true, hasLevels: Boolean = true) =
        VisRow(
            delaysMs = FloatArray(3), levelsDb = FloatArray(3), numOutputs = 2, numReverbs = 1,
            revision = ++revision, hasDelays = hasDelays, hasLevels = hasLevels,
            receivedAtMs = receivedAtMs
        )

    private fun state(rows: Map<Int, VisRow>, numOutputs: Int = 2, primary: Int = 1,
                      selection: List<Int> = emptyList()) =
        VisualisationState(primaryChannel = primary, selectionSet = selection,
            numOutputs = numOutputs, numReverbs = 1, rows = rows)

    private fun inventory(vararg numbers: Int) =
        ChannelInventory(numbers.map { ChannelInfo(it, isStereo = false) })

    // --- displayedVisChannels ---

    @Test
    fun thePinWinsOverTheSelection() {
        val s = state(emptyMap(), selection = listOf(2, 3))
        assertEquals(listOf(7), displayedVisChannels(s, 7, inventory(1, 2, 3, 7)))
    }

    @Test
    fun aSelectionIsShownWhole() {
        val s = state(emptyMap(), selection = listOf(3, 2))
        assertEquals(listOf(3, 2), displayedVisChannels(s, 0, inventory(1, 2, 3)))
    }

    @Test
    fun aLivePrimaryIsShown() {
        assertEquals(listOf(4), displayedVisChannels(state(emptyMap(), primary = 4), 0, inventory(9, 4)))
    }

    @Test
    fun aPrimaryThatIsNotLiveGivesWayToTheFirstLiveChannelInDisplayOrder() {
        assertEquals(listOf(9), displayedVisChannels(state(emptyMap(), primary = 1), 0, inventory(9, 4)))
    }

    @Test
    fun anInventoryNotKnownYetKeepsThePrimary() {
        assertEquals(listOf(1), displayedVisChannels(state(emptyMap(), primary = 1), 0, ChannelInventory()))
    }

    // --- VisRefreshWatchdog ---

    @Test
    fun asksOnEntryEvenWhenTheDataLooksCompleteAndFresh() {
        val w = VisRefreshWatchdog()
        val s = state(mapOf(1 to row(receivedAtMs = 1000)))
        assertEquals(Action.REQUEST, w.next(s, listOf(1), nowMs = 1500))
        assertEquals(Action.NONE, w.next(s, listOf(1), nowMs = 3500))
    }

    @Test
    fun withoutAConfigAsksSixTimesThenFallsBackToOneResync() {
        val w = VisRefreshWatchdog()
        val s = state(emptyMap(), numOutputs = 0)
        repeat(6) { assertEquals("attempt ${it + 1}", Action.REQUEST, w.next(s, listOf(1), nowMs = 0)) }
        assertEquals(Action.RESYNC, w.next(s, listOf(1), nowMs = 0))
        assertEquals(Action.NONE, w.next(s, listOf(1), nowMs = 0))
        assertEquals(Action.NONE, w.next(s, listOf(1), nowMs = 0))
    }

    @Test
    fun theResyncIsOnlyForAMissingConfig() {
        // Rows missing but the config came: the budget runs out and the tab waits for
        // pushes, since a re-dump would not bring rows either.
        val w = VisRefreshWatchdog()
        val s = state(emptyMap())
        repeat(6) { assertEquals(Action.REQUEST, w.next(s, listOf(1), nowMs = 0)) }
        assertEquals(Action.NONE, w.next(s, listOf(1), nowMs = 0))
    }

    @Test
    fun aMissingHalfRowOrStaleRowsNeedARefresh() {
        val w = VisRefreshWatchdog()
        val now = 10_000L
        assertTrue(w.needsRefresh(state(emptyMap()), listOf(1), now))
        assertTrue(w.needsRefresh(state(mapOf(1 to row(now, hasLevels = false))), listOf(1), now))
        assertTrue(w.needsRefresh(state(mapOf(1 to row(now, hasDelays = false))), listOf(1), now))
        assertFalse(w.needsRefresh(state(mapOf(1 to row(now - 6000))), listOf(1), now))
        assertTrue(w.needsRefresh(state(mapOf(1 to row(now - 6001))), listOf(1), now))
    }

    @Test
    fun aMultiSelectionIsStaleOnlyWhenEveryRowIs() {
        val w = VisRefreshWatchdog()
        val now = 10_000L
        val someFresh = state(mapOf(1 to row(now - 9000), 2 to row(now - 100)), selection = listOf(1, 2))
        assertFalse(w.needsRefresh(someFresh, listOf(1, 2), now))
        val oneMissing = state(mapOf(1 to row(now)), selection = listOf(1, 2))
        assertTrue(w.needsRefresh(oneMissing, listOf(1, 2), now))
    }

    @Test
    fun freshRowsGiveTheNextGapAFreshBudget() {
        val w = VisRefreshWatchdog()
        val stale = state(mapOf(1 to row(receivedAtMs = 0)))
        repeat(6) { assertEquals(Action.REQUEST, w.next(stale, listOf(1), nowMs = 60_000)) }
        assertEquals(Action.NONE, w.next(stale, listOf(1), nowMs = 60_000))
        // A push finally lands, then the desktop goes quiet again.
        val refreshed = state(mapOf(1 to row(receivedAtMs = 60_000)))
        assertEquals(Action.NONE, w.next(refreshed, listOf(1), nowMs = 61_000))
        assertEquals(Action.REQUEST, w.next(refreshed, listOf(1), nowMs = 70_000))
    }
}
