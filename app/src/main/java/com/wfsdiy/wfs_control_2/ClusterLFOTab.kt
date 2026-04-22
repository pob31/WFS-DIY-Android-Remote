package com.wfsdiy.wfs_control_2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ClusterLFOTab(
    clusterLFOActive: IntArray,
    presetNames: Array<String>,
    presetPopulated: BooleanArray,
    presetAxes: IntArray = IntArray(16) { 0 },  // bitmask per preset: X=1 Y=2 Z=4 R=8 S=16
    onPresetRecall: (clusterId: Int, presetNumber: Int) -> Unit,
    onPresetRecallAndActivate: (clusterId: Int, presetNumber: Int) -> Unit,
    onClusterLFOToggle: (clusterId: Int, active: Int) -> Unit,
    onStopAll: () -> Unit
) {
    var selectedCluster by remember { mutableIntStateOf(1) }

    Row(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // Left: cluster selectors + LFO toggles
        Column(modifier = Modifier.weight(0.28f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for (c in 1..10) {
                val isSelected = c == selectedCluster
                val clusterColor = getMarkerColor(c, isClusterMarker = true)
                val isActive = clusterLFOActive.getOrElse(c - 1) { 0 } != 0
                Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) clusterColor.copy(alpha = 0.8f) else clusterColor.copy(alpha = 0.25f))
                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) clusterColor else clusterColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .pointerInput(c) { detectTapGestures { selectedCluster = c } },
                        contentAlignment = Alignment.Center
                    ) { Text("$c", color = if (isSelected) Color.White else clusterColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp) }
                    Box(modifier = Modifier.width(36.dp).fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isActive) Color(0xFF4CAF50).copy(alpha = 0.7f) else Color(0xFF424242))
                        .border(1.dp, if (isActive) Color(0xFF4CAF50) else Color(0xFF616161), RoundedCornerShape(6.dp))
                        .pointerInput(c) { detectTapGestures { onClusterLFOToggle(c, if (isActive) 0 else 1) } },
                        contentAlignment = Alignment.Center
                    ) { Text(if (isActive) "ON" else "OFF", color = if (isActive) Color.White else Color(0xFF9E9E9E), fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFB71C1C).copy(alpha = 0.6f))
                .border(1.dp, Color(0xFFB71C1C), RoundedCornerShape(6.dp))
                .pointerInput(Unit) { detectTapGestures { onStopAll() } },
                contentAlignment = Alignment.Center
            ) { Text("ALL OFF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right: 4x4 preset grid
        Column(modifier = Modifier.weight(0.72f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in 0..3) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (col in 0..3) {
                        val idx = row * 4 + col
                        val pNum = idx + 1
                        val name = presetNames.getOrElse(idx) { "" }
                        val populated = presetPopulated.getOrElse(idx) { false }
                        val axes = presetAxes.getOrElse(idx) { 0 }
                        val displayName = if (name.isNotEmpty()) name else if (populated) "$pNum" else ""
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (populated) Color(0xFF2C2C2C) else Color(0xFF1A1A1A))
                            .border(1.dp, if (populated) Color(0xFF555555) else Color(0xFF333333), RoundedCornerShape(8.dp))
                            .pointerInput(pNum, selectedCluster) {
                                if (populated) detectTapGestures(
                                    onDoubleTap = { onPresetRecallAndActivate(selectedCluster, pNum) },
                                    onTap = { onPresetRecall(selectedCluster, pNum) }
                                )
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            if (displayName.isNotEmpty()) Text(displayName, color = if (populated) Color(0xFFE0E0E0) else Color(0xFF555555),
                                fontSize = 24.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(4.dp))

                            // Automated axes indicator (bottom-right), matches JUCE pattern:
                            // X/Y/Z green, R blue, S purple.
                            if (populated && axes != 0) {
                                Row(
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    val letters = listOf(
                                        "X" to (axes and 1 != 0),
                                        "Y" to (axes and 2 != 0),
                                        "Z" to (axes and 4 != 0),
                                        "R" to (axes and 8 != 0),
                                        "S" to (axes and 16 != 0)
                                    )
                                    for ((ch, on) in letters) {
                                        if (!on) continue
                                        val color = when (ch) {
                                            "R" -> Color(0xFF42A5F5)
                                            "S" -> Color(0xFFAB47BC)
                                            else -> Color(0xFF66BB6A)
                                        }
                                        Text(ch, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
