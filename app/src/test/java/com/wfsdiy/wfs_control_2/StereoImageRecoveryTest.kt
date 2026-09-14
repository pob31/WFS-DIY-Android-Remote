package com.wfsdiy.wfs_control_2

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * stereoChannelsMissingImage: which stereo pairs still lack the width / axis offset the
 * map's spread bar is drawn from, so the tablet asks for their channel dumps.
 */
class StereoImageRecoveryTest {

    private fun value(v: Float) = InputParameterValue(normalizedValue = v)

    private fun state(vararg channels: Pair<Int, Map<String, InputParameterValue>>) =
        InputParametersState(
            channels = channels.associate { (n, p) ->
                n to InputChannelState(n, p.toMutableMap())
            }.toMutableMap()
        )

    private val image = mapOf("stereoWidth" to value(0.4f), "stereoAxisOffset" to value(0.5f))
    private val nameOnly = mapOf("inputName" to InputParameterValue(stringValue = "Keys"))

    @Test
    fun aPairAnnouncedWithItsNameAndPositionOnlyIsMissing() {
        // What a pair added mid-session looked like: listed stereo, name and
        // position known, no stereo image.
        val inventory = ChannelInventory(listOf(ChannelInfo(1, false), ChannelInfo(9, true)))
        assertEquals(listOf(9), stereoChannelsMissingImage(inventory, state(1 to nameOnly, 9 to nameOnly)))
    }

    @Test
    fun aPairWithItsImageIsNot() {
        val inventory = ChannelInventory(listOf(ChannelInfo(7, true)))
        assertEquals(emptyList<Int>(), stereoChannelsMissingImage(inventory, state(7 to image)))
    }

    @Test
    fun aPairTheTabletHasNoParametersForIsMissing() {
        val inventory = ChannelInventory(listOf(ChannelInfo(4, true)))
        assertEquals(listOf(4), stereoChannelsMissingImage(inventory, state()))
    }

    @Test
    fun aWidthWithoutAnAxisOffsetIsNotEnough() {
        val inventory = ChannelInventory(listOf(ChannelInfo(2, true)))
        val widthOnly = mapOf("stereoWidth" to value(0.4f))
        assertEquals(listOf(2), stereoChannelsMissingImage(inventory, state(2 to widthOnly)))
    }

    @Test
    fun theAxisLockIsNotRequired() {
        // Desktops before 1.0.0beta46 never send it; asking for it would never end.
        val inventory = ChannelInventory(listOf(ChannelInfo(3, true)))
        assertEquals(emptyList<Int>(), stereoChannelsMissingImage(inventory, state(3 to image)))
    }

    @Test
    fun monoChannelsAreNeverAskedFor() {
        val inventory = ChannelInventory(listOf(ChannelInfo(1, false), ChannelInfo(2, false)))
        assertEquals(emptyList<Int>(), stereoChannelsMissingImage(inventory, state(1 to nameOnly)))
    }

    @Test
    fun theDisplayOrderIsKept() {
        val inventory = ChannelInventory(
            listOf(ChannelInfo(8, true), ChannelInfo(2, true), ChannelInfo(5, true)))
        assertEquals(listOf(8, 5), stereoChannelsMissingImage(inventory, state(2 to image)))
    }
}
