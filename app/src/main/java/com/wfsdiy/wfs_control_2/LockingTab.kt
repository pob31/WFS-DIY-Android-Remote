package com.wfsdiy.wfs_control_2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import CustomToggleButton
import com.wfsdiy.wfs_control_2.localization.loc

@Composable
fun LockingTab(
    numberOfInputs: Int,
    markers: List<Marker>, // Marker is defined in MainActivity.kt (same package)
    onMarkersChanged: (List<Marker>) -> Unit
) {
    val itemsPerRow = 10

    val onLockChanged = { markerToToggle: Marker ->
        val updatedMarkersList = markers.map { m ->
            if (m.id == markerToToggle.id) {
                m.copy(isLocked = !m.isLocked)
            } else {
                m
            }
        }
        onMarkersChanged(updatedMarkersList)
    }

    val inputMarkers = markers.take(numberOfInputs)
    val rowsOfInputMarkers = inputMarkers.chunked(itemsPerRow)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(rowsOfInputMarkers) { rowMarkers ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Top
            ) {
                rowMarkers.forEach { marker ->
                    val markerName = marker.name.takeIf { it.isNotBlank() }
                    val lockPrefix = loc("remote.lock.prefix")
                    val buttonText = "$lockPrefix ${marker.id}" +
                            if (markerName != null) "\n\n${markerName.take(12)}" else ""

                    CustomToggleButton(
                        text = buttonText,
                        isOn = marker.isLocked,
                        onColor = Color.Black,
                        offColor = Color.DarkGray,
                        borderColor = getMarkerColor(marker.id, isClusterMarker = false),
                        onTextColor = Color.Red,
                        offTextColor = Color.White,
                        onClick = { onLockChanged(marker) },
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 60.dp, max = 100.dp)
                    )
                }

                val numberOfPlaceholders = itemsPerRow - rowMarkers.size
                if (numberOfPlaceholders > 0) {
                    repeat(numberOfPlaceholders) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
