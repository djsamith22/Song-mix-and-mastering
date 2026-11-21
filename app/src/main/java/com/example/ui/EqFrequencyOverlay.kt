package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.BandTelemetry
import com.example.audio.MasteringParams
import com.example.ui.theme.StudioColors
import com.example.ui.theme.StudioMonoFontFamily
import com.example.ui.theme.StudioSansFontFamily
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private const val MIN_FREQ_HZ = 20.0
private const val MAX_FREQ_HZ = 20000.0
private const val MIN_DB = -12f
private const val MAX_DB = 9f

private fun freqToX(freqHz: Double, width: Float): Float {
    val logMin = log10(MIN_FREQ_HZ)
    val logMax = log10(MAX_FREQ_HZ)
    val frac = ((log10(freqHz.coerceIn(MIN_FREQ_HZ, MAX_FREQ_HZ)) - logMin) / (logMax - logMin))
        .toFloat()
        .coerceIn(0f, 1f)
    return frac * width
}

private fun xToFreq(x: Float, width: Float): Float {
    val frac = (x / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val logMin = log10(MIN_FREQ_HZ)
    val logMax = log10(MAX_FREQ_HZ)
    return 10.0.pow(logMin + frac * (logMax - logMin)).toFloat()
}

private fun dbToY(db: Float, height: Float): Float {
    val norm = ((MAX_DB - db.coerceIn(MIN_DB, MAX_DB)) / (MAX_DB - MIN_DB)).coerceIn(0f, 1f)
    return norm * height
}

private fun yToDb(y: Float, height: Float): Float {
    val frac = (y / height.coerceAtLeast(1f)).coerceIn(0f, 1f)
    return MAX_DB - frac * (MAX_DB - MIN_DB)
}

/**
 * Interactive logarithmic EQ frequency curve overlay displaying live per-band signal energy,
 * real-time Gain Reduction drop meters at each band frequency, and draggable EQ nodes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqFrequencyOverlay(
    params: MasteringParams,
    eqCurveDb: List<Float>,
    bandTelemetry: List<BandTelemetry>,
    isBypassed: Boolean,
    isPlaying: Boolean,
    onUpdateParams: ((MasteringParams) -> MasteringParams) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedBand by remember { mutableIntStateOf(2) } // Default focus: 250 Hz Mud Cut

    val activeTelemetry = bandTelemetry.getOrNull(selectedBand)
    val curveColor = if (isBypassed) StudioColors.TextSecondary else StudioColors.Amber
    val grBarColor = Color(0xFFFB7185) // Studio Coral for Gain Reduction meter

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(StudioColors.Background)
            .border(1.dp, StudioColors.Border, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header with live active band readout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "EQ Curve & Real-Time Band GR",
                fontFamily = StudioSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = StudioColors.TextSecondary
            )
            if (activeTelemetry != null) {
                val eqStr = if (activeTelemetry.bandIndex == 0) {
                    "${params.hpf.roundToInt()} Hz"
                } else {
                    String.format(
                        "%s%.1f dB",
                        if (activeTelemetry.eqGainDb >= 0f) "+" else "",
                        activeTelemetry.eqGainDb
                    )
                }
                val grStr = String.format("GR %.1f dB", activeTelemetry.totalGrDb)
                Text(
                    text = "${activeTelemetry.shortLabel} · $eqStr · $grStr",
                    fontFamily = StudioMonoFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (activeTelemetry.totalGrDb < -0.2f && !isBypassed) {
                        grBarColor
                    } else {
                        StudioColors.AmberBright
                    },
                    modifier = Modifier.testTag("eq_overlay_readout")
                )
            }
        }

        // Interactive Canvas Overlay
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(152.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF080C12))
                .border(1.dp, StudioColors.Border, RoundedCornerShape(8.dp))
                .testTag("eq_frequency_overlay_canvas")
                .semantics { contentDescription = "Interactive Parametric Repair EQ frequency and gain reduction overlay" }
                .pointerInput(params) {
                    detectTapGestures { tapOffset ->
                        val bandFreqs = floatArrayOf(
                            params.hpf,
                            params.lowFreq,
                            params.mudFreq,
                            params.presFreq,
                            params.deessFreq,
                            params.airFreq
                        )
                        var nearest = 0
                        var minDist = Float.MAX_VALUE
                        for (i in bandFreqs.indices) {
                            val bx = freqToX(bandFreqs[i].toDouble(), size.width.toFloat())
                            val dist = abs(tapOffset.x - bx)
                            if (dist < minDist) {
                                minDist = dist
                                nearest = i
                            }
                        }
                        selectedBand = nearest
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            // Pick nearest node at drag start based on latest params from telemetry/current coordinates
                            val freqs = if (bandTelemetry.size == 6) {
                                bandTelemetry.map { it.freqHz }
                            } else {
                                listOf(
                                    params.hpf,
                                    params.lowFreq,
                                    params.mudFreq,
                                    params.presFreq,
                                    params.deessFreq,
                                    params.airFreq
                                )
                            }
                            var nearest = 0
                            var minDist = Float.MAX_VALUE
                            freqs.forEachIndexed { idx, f ->
                                val bx = freqToX(f.toDouble(), size.width.toFloat())
                                val d = abs(startOffset.x - bx)
                                if (d < minDist) {
                                    minDist = d
                                    nearest = idx
                                }
                            }
                            selectedBand = nearest
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val pos = change.position
                            val dragFreq = xToFreq(pos.x, w)
                            when (selectedBand) {
                                0 -> {
                                    val clamped = dragFreq.coerceIn(20f, 250f)
                                    val snapped = ((clamped / 2f).roundToInt() * 2f).coerceIn(20f, 250f)
                                    onUpdateParams { it.copy(hpf = snapped) }
                                }
                                1 -> {
                                    val db = snapDb(yToDb(pos.y, h), -6f, 6f)
                                    val hz = dragFreq.roundToInt().coerceIn(40, 300).toFloat()
                                    onUpdateParams { it.copy(low = db, lowFreq = hz) }
                                }
                                2 -> {
                                    val db = snapDb(yToDb(pos.y, h), -6f, 4f)
                                    val hz = dragFreq.roundToInt().coerceIn(120, 1000).toFloat()
                                    onUpdateParams { it.copy(mud = db, mudFreq = hz) }
                                }
                                3 -> {
                                    val db = snapDb(yToDb(pos.y, h), -4f, 6f)
                                    val hz = dragFreq.roundToInt().coerceIn(800, 8000).toFloat()
                                    onUpdateParams { it.copy(pres = db, presFreq = hz) }
                                }
                                4 -> {
                                    val db = snapDb(yToDb(pos.y, h), -6f, 3f)
                                    val hz = dragFreq.roundToInt().coerceIn(2500, 14000).toFloat()
                                    onUpdateParams { it.copy(deess = db, deessFreq = hz) }
                                }
                                5 -> {
                                    val db = snapDb(yToDb(pos.y, h), -4f, 6f)
                                    val hz = dragFreq.roundToInt().coerceIn(6000, 18000).toFloat()
                                    onUpdateParams { it.copy(air = db, airFreq = hz) }
                                }
                            }
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            val zeroY = dbToY(0f, h)

            // 1. Subtle log frequency gridlines & horizontal dB reference lines
            val gridFreqs = listOf(30.0, 90.0, 250.0, 1000.0, 3200.0, 6800.0, 11000.0)
            gridFreqs.forEach { gf ->
                val gx = freqToX(gf, w)
                drawLine(
                    color = StudioColors.Border.copy(alpha = 0.55f),
                    start = Offset(gx, 0f),
                    end = Offset(gx, h),
                    strokeWidth = 1.dp.toPx()
                )
            }

            listOf(6f, 0f, -6f).forEach { dbRef ->
                val ry = dbToY(dbRef, h)
                drawLine(
                    color = if (dbRef == 0f) {
                        StudioColors.BorderSubtle
                    } else {
                        StudioColors.Border.copy(alpha = 0.4f)
                    },
                    start = Offset(0f, ry),
                    end = Offset(w, ry),
                    strokeWidth = if (dbRef == 0f) 1.2.dp.toPx() else 0.8.dp.toPx(),
                    pathEffect = if (dbRef == 0f) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            }

            // 2. Real-time Per-Band Energy Glow + Gain Reduction Drop Bars
            val bandBarW = 16.dp.toPx()
            bandTelemetry.forEach { bt ->
                val cx = freqToX(bt.freqHz.toDouble(), w)

                // Live band energy column rising from bottom when playing
                if (isPlaying && bt.levelNorm > 0.02f) {
                    val energyH = bt.levelNorm * (h * 0.45f)
                    drawRoundRect(
                        color = StudioColors.EmeraldBright.copy(alpha = 0.14f),
                        topLeft = Offset(cx - bandBarW * 0.8f, h - energyH),
                        size = Size(bandBarW * 1.6f, energyH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }

                // Real-time Gain Reduction drop bar extending downward from 0 dB
                if (!isBypassed && bt.totalGrDb < -0.15f) {
                    val grEndY = dbToY(bt.totalGrDb, h)
                    val dropH = (grEndY - zeroY).coerceAtLeast(2.dp.toPx())
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                grBarColor.copy(alpha = 0.55f),
                                StudioColors.Amber.copy(alpha = 0.22f)
                            ),
                            startY = zeroY,
                            endY = zeroY + dropH
                        ),
                        topLeft = Offset(cx - bandBarW / 2f, zeroY),
                        size = Size(bandBarW, dropH),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )

                    // Crisp dynamic GR cap line at current reduction depth
                    drawLine(
                        color = grBarColor,
                        start = Offset(cx - bandBarW / 2f, zeroY + dropH),
                        end = Offset(cx + bandBarW / 2f, zeroY + dropH),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // 3. Combined Parametric EQ Curve
            if (eqCurveDb.size >= 2) {
                val curvePath = Path()
                val fillPath = Path()
                val logMin = log10(MIN_FREQ_HZ)
                val logMax = log10(MAX_FREQ_HZ)

                eqCurveDb.forEachIndexed { idx, db ->
                    val f = 10.0.pow(logMin + (idx.toDouble() / (eqCurveDb.size - 1)) * (logMax - logMin))
                    val x = freqToX(f, w)
                    val y = dbToY(db, h)
                    if (idx == 0) {
                        curvePath.moveTo(x, y)
                        fillPath.moveTo(x, zeroY)
                        fillPath.lineTo(x, y)
                    } else {
                        curvePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                    if (idx == eqCurveDb.lastIndex) {
                        fillPath.lineTo(x, zeroY)
                        fillPath.close()
                    }
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            curveColor.copy(alpha = 0.22f),
                            curveColor.copy(alpha = 0.04f)
                        )
                    )
                )

                drawPath(
                    path = curvePath,
                    color = curveColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 4. Six Interactive EQ Control Nodes
            val nodePositions = listOf(
                Pair(0, params.hpf.toDouble() to (bandTelemetry.getOrNull(0)?.eqGainDb ?: -3f)),
                Pair(1, params.lowFreq.toDouble() to params.low),
                Pair(2, params.mudFreq.toDouble() to params.mud),
                Pair(3, params.presFreq.toDouble() to params.pres),
                Pair(4, params.deessFreq.toDouble() to params.deess),
                Pair(5, params.airFreq.toDouble() to params.air)
            )
            nodePositions.forEach { (idx, freqAndDb) ->
                val nx = freqToX(freqAndDb.first, w)
                val ny = dbToY(freqAndDb.second, h)
                val isSelected = idx == selectedBand
                val nodeRadius = if (isSelected) 6.5.dp.toPx() else 4.5.dp.toPx()

                if (isSelected) {
                    drawCircle(
                        color = curveColor.copy(alpha = 0.25f),
                        radius = 12.dp.toPx(),
                        center = Offset(nx, ny)
                    )
                }
                drawCircle(
                    color = StudioColors.Background,
                    radius = nodeRadius,
                    center = Offset(nx, ny)
                )
                drawCircle(
                    color = if (isSelected) StudioColors.AmberCream else curveColor,
                    radius = nodeRadius,
                    center = Offset(nx, ny),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Real-time 6-band Gain Reduction chips directly under the overlay
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            bandTelemetry.forEach { bt ->
                val isSelected = bt.bandIndex == selectedBand
                val hasReduction = !isBypassed && bt.totalGrDb < -0.15f
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isSelected) StudioColors.Amber.copy(alpha = 0.14f) else StudioColors.Surface
                        )
                        .border(
                            1.dp,
                            if (isSelected) StudioColors.Amber else StudioColors.Border,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { selectedBand = bt.bandIndex }
                        .minimumInteractiveComponentSize()
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("band_gr_chip_${bt.bandIndex}"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = bt.shortLabel,
                        fontFamily = StudioMonoFontFamily,
                        fontSize = 10.sp,
                        color = StudioColors.TextSecondary
                    )
                    Text(
                        text = String.format("%.1f dB", if (isBypassed) 0f else bt.totalGrDb),
                        fontFamily = StudioMonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = if (hasReduction) grBarColor else StudioColors.TextPrimary
                    )
                }
            }
        }

        // Manual Hz frequency input boxes directly below the interactive EQ overlay
        EqBandFrequencyInputs(
            params = params,
            selectedBand = selectedBand,
            onSelectBand = { selectedBand = it },
            onUpdateParams = onUpdateParams
        )
    }
}

private fun snapDb(rawDb: Float, minVal: Float, maxVal: Float): Float {
    val clamped = rawDb.coerceIn(minVal, maxVal)
    return ((clamped / 0.2f).roundToInt() * 0.2f).coerceIn(minVal, maxVal)
}
