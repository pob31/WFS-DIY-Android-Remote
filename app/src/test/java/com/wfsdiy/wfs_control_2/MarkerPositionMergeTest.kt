package com.wfsdiy.wfs_control_2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * mergeMarkerPositions: the Map's position write-back must not carry its stale copy
 * of anything else back over the current markers.
 */
class MarkerPositionMergeTest {

    private fun marker(id: Int, x: Float, y: Float, name: String = "", cluster: Int = 0,
                       locked: Boolean = false, visible: Boolean = true) =
        Marker(id, x, y, radius = 14f, isLocked = locked, isVisible = visible,
               name = name, clusterId = cluster)

    @Test
    fun aNameThatArrivedAfterTheMapComposedIsKept() {
        // The case behind the blank label: the name sync set "Keys" on channel 9, and
        // the Map's list, composed just before, still has it unnamed.
        val current = listOf(marker(8, 10f, 10f, "Bass"), marker(9, 0f, 0f, "Keys"))
        val fromMap = listOf(marker(8, 10f, 10f, "Bass"), marker(9, 120f, 80f, ""))
        val merged = mergeMarkerPositions(current, fromMap)
        assertEquals("Keys", merged[1].name)
        assertEquals(120f, merged[1].positionX)
        assertEquals(80f, merged[1].positionY)
    }

    @Test
    fun clusterLockAndVisibilityComeFromTheCurrentList() {
        val current = listOf(marker(3, 5f, 5f, "Vox", cluster = 2, locked = true, visible = false))
        val fromMap = listOf(marker(3, 50f, 60f, "Vox", cluster = 0, locked = false, visible = true))
        val merged = mergeMarkerPositions(current, fromMap).single()
        assertEquals(2, merged.clusterId)
        assertEquals(true, merged.isLocked)
        assertEquals(false, merged.isVisible)
        assertEquals(50f, merged.positionX)
    }

    @Test
    fun aMarkerTheMapDidNotSendIsUntouched() {
        // The drag sends only the dragged marker.
        val other = marker(1, 7f, 7f, "Kick")
        val current = listOf(other, marker(2, 0f, 0f, "Snare"))
        val merged = mergeMarkerPositions(current, listOf(marker(2, 30f, 40f)))
        assertSame(other, merged[0])
        assertEquals(30f, merged[1].positionX)
        assertEquals("Snare", merged[1].name)
    }

    @Test
    fun anUnmovedMarkerStaysTheSameObject() {
        val same = marker(4, 12f, 34f, "Pad")
        val merged = mergeMarkerPositions(listOf(same), listOf(marker(4, 12f, 34f, "")))
        assertSame(same, merged.single())
    }

    @Test
    fun theCurrentOrderIsKept() {
        val current = listOf(marker(5, 0f, 0f), marker(6, 0f, 0f), marker(7, 0f, 0f))
        val merged = mergeMarkerPositions(current, listOf(marker(7, 1f, 1f), marker(5, 2f, 2f)))
        assertEquals(listOf(5, 6, 7), merged.map { it.id })
    }
}
