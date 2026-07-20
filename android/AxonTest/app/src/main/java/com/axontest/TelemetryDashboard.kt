package com.vectr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TelemetrySample(
    val timestampMs: Long,
    val cpu: Float,
    val ram: Float,
    val gpu: Float? = null,
)

private val Graphite = Color(UiTokens.BACKGROUND_GRAPHITE)
private val Surface = Color(UiTokens.SURFACE_GRAPHITE)
private val Accent = Color(UiTokens.ACCENT_AMBER)
private val Warning = Color(0xFFF0B35F)
private val Hot = Color(0xFFF27986)
private val Mono = FontFamily(Font(R.font.jetbrains_mono_regular))

@Composable
fun TelemetryDashboard(samples: List<TelemetrySample>, cpuTemp: Float?, gpuTemp: Float?, gpuAvailable: Boolean) {
    Column(Modifier.fillMaxSize().background(Graphite).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("TELEMETRY", color = Accent, fontFamily = Mono, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GraphCard("CPU USAGE", samples.map { it.cpu }, Modifier.weight(1f))
            GraphCard("RAM USAGE", samples.map { it.ram }, Modifier.weight(1f))
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (gpuAvailable) {
                GaugeCard("CPU TEMP", cpuTemp, Modifier.weight(1f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    GraphCard("GPU USAGE", samples.mapNotNull { it.gpu }, Modifier.weight(1f))
                    GaugeCard("GPU TEMP", gpuTemp, Modifier.weight(1f))
                }
            } else {
                // GPU telemetry is unavailable on many systems, so omit that section.
                GaugeCard("CPU TEMP", cpuTemp, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun GraphCard(title: String, values: List<Float>, modifier: Modifier = Modifier) {
    Column(modifier.background(Surface).padding(14.dp)) {
        Text(title, color = Color(0xFFD7E9DAD0), fontFamily = Mono, fontSize = 12.sp)
        Canvas(Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp)) {
            val points = values
            if (points.size < 2) return@Canvas
            val step = size.width / (points.size - 1)
            for (index in 1 until points.size) {
                val x1 = step * (index - 1)
                val y1 = size.height * (1f - points[index - 1].coerceIn(0f, 100f) / 100f)
                val x2 = step * index
                val y2 = size.height * (1f - points[index].coerceIn(0f, 100f) / 100f)
                drawLine(Accent, androidx.compose.ui.geometry.Offset(x1, y1), androidx.compose.ui.geometry.Offset(x2, y2), strokeWidth = 4f, cap = StrokeCap.Round)
            }
        }
        Text(values.lastOrNull()?.let { "${it.toInt()}%" } ?: "WAITING", color = Accent, fontFamily = Mono, fontSize = 15.sp)
    }
}

@Composable
private fun GaugeCard(title: String, temperature: Float?, modifier: Modifier = Modifier) {
    val color = when { temperature == null -> Color(0xFFD7E9DAD0); temperature >= 85f -> Hot; temperature >= 70f -> Warning; else -> Accent }
    Column(modifier.background(Surface).padding(14.dp)) {
        Text(title, color = Color(0xFFD7E9DAD0), fontFamily = Mono, fontSize = 12.sp)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(18.dp)) {
                drawArc(Color(0x3359FFF4), -225f, 270f, false, style = Stroke(16f, cap = StrokeCap.Round))
                if (temperature != null) drawArc(color, -225f, 270f * (temperature.coerceIn(0f, 100f) / 100f), false, style = Stroke(16f, cap = StrokeCap.Round))
            }
            Text(temperature?.let { "${it.toInt()}°C" } ?: "N/A", color = color, fontFamily = Mono, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}
