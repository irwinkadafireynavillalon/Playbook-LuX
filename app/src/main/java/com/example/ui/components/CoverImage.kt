package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.effects.LocalLuxReduceEffects
import com.example.ui.effects.lux25DCoverTilt
import com.example.ui.theme.LuxDeepPurple
import com.example.ui.theme.LuxGoldAccent
import com.example.ui.theme.LuxGoldBright
import com.example.ui.theme.LuxPurplePrimary
import java.io.File

@Composable
fun BookCoverImage(
    coverPath: String?,
    title: String,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    enable25DTilt: Boolean = false,
    interactiveTouchTilt: Boolean = false,
    onHorizontalSwipe: ((direction: Int) -> Unit)? = null
) {
    val file = coverPath?.let { File(it) }
    val hasValidFile = file != null && file.exists() && file.length() > 0
    val reduceEffects = LocalLuxReduceEffects.current

    val tiltModifier = if (enable25DTilt) {
        Modifier.lux25DCoverTilt(
            interactiveTouch = interactiveTouchTilt,
            maxTiltDeg = if (interactiveTouchTilt) 9f else 4f,
            enableGyro = true,
            onHorizontalSwipe = onHorizontalSwipe
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(tiltModifier)
            .clip(shape)
            .border(1.dp, LuxGoldAccent.copy(alpha = 0.45f), shape)
            .testTag("book_cover_image"),
        contentAlignment = Alignment.Center
    ) {
        if (hasValidFile) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(file)
                    .crossfade(true)
                    .build(),
                contentDescription = "Portada de $title",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val initial = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "L"
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                LuxDeepPurple,
                                LuxPurplePrimary,
                                Color(0xFF2C164D)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.6f)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF1E1038))
                        .border(2.dp, LuxGoldAccent, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        color = LuxGoldBright,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Subtle 2.5D diagonal specular gloss overlay (no text obstruction)
        if (enable25DTilt && !reduceEffects) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.14f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.16f)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    )
                )
            }
        }
    }
}
