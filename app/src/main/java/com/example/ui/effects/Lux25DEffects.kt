package com.example.ui.effects

import android.content.Context
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.R
import com.example.ui.theme.LuxDeepPurple
import com.example.ui.theme.LuxGoldAccent
import com.example.ui.theme.LuxGoldBright
import com.example.ui.theme.LuxSilverBright
import kotlinx.coroutines.delay

/**
 * Global toggle propagating effective reduced-effects state across Compose tree.
 */
val LocalLuxReduceEffects = staticCompositionLocalOf { false }

/**
 * Checks user toggle + Android system ANIMATOR_DURATION_SCALE == 0 + PowerManager Battery Saver.
 */
@Composable
fun rememberEffectiveReduceEffects(userSetting: Boolean): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var systemReduced by remember { mutableStateOf(checkSystemReduced(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                systemReduced = checkSystemReduced(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return userSetting || systemReduced
}

private fun checkSystemReduced(context: Context): Boolean {
    val animScale = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    val powerSaver = runCatching {
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    }.getOrDefault(false)
    return animScale == 0f || powerSaver
}

/**
 * Lightweight lifecycle-aware gyroscope parallax reader (Sensor.TYPE_ROTATION_VECTOR).
 * Unregisters cleanly on ON_PAUSE or when reduceEffects is enabled.
 */
@Composable
fun rememberGyroParallax(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val offsetState = remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(enabled, lifecycleOwner, context) {
        if (!enabled) {
            offsetState.value = Offset.Zero
            return@DisposableEffect onDispose {}
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensorManager == null || rotationSensor == null) {
            return@DisposableEffect onDispose {}
        }

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        var basePitch: Float? = null
        var baseRoll: Float? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val pitch = orientation[1]
                val roll = orientation[2]
                if (basePitch == null || baseRoll == null) {
                    basePitch = pitch
                    baseRoll = roll
                }
                val dx = ((roll - (baseRoll ?: roll)) * 2.5f).coerceIn(-1f, 1f)
                val dy = ((pitch - (basePitch ?: pitch)) * 2.5f).coerceIn(-1f, 1f)
                offsetState.value = Offset(dx, dy)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        var registered = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (!registered) {
                        registered = sensorManager.registerListener(
                            listener,
                            rotationSensor,
                            SensorManager.SENSOR_DELAY_UI
                        )
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    if (registered) {
                        sensorManager.unregisterListener(listener)
                        registered = false
                    }
                }
                else -> {}
            }
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            registered = sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (registered) {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    return offsetState
}

/**
 * 2.5D depth tilt modifier using GPU graphicsLayer.
 * Set [interactiveTouch] = true on PlayerScreen & BookDetailScreen covers for direct touch tilt;
 * keep [interactiveTouch] = false on scrolling LazyGrid/LazyColumn cards to preserve 60fps scroll.
 */
fun Modifier.lux25DCoverTilt(
    interactiveTouch: Boolean = false,
    maxTiltDeg: Float = 7f,
    enableGyro: Boolean = true,
    onHorizontalSwipe: ((direction: Int) -> Unit)? = null
): Modifier = composed {
    val reduceEffects = LocalLuxReduceEffects.current
    if (reduceEffects) return@composed this

    val gyro by rememberGyroParallax(enabled = enableGyro)
    var touchTiltX by remember { mutableFloatStateOf(0f) }
    var touchTiltY by remember { mutableFloatStateOf(0f) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }

    val animRotX by animateFloatAsState(
        targetValue = (touchTiltX + gyro.y * maxTiltDeg * 0.65f).coerceIn(-maxTiltDeg, maxTiltDeg),
        animationSpec = spring(stiffness = 280f, dampingRatio = 0.72f),
        label = "luxRotX"
    )
    val animRotY by animateFloatAsState(
        targetValue = (touchTiltY + gyro.x * maxTiltDeg * 0.65f).coerceIn(-maxTiltDeg, maxTiltDeg),
        animationSpec = spring(stiffness = 280f, dampingRatio = 0.72f),
        label = "luxRotY"
    )

    val pointerMod = if (interactiveTouch) {
        Modifier.pointerInput(onHorizontalSwipe) {
            detectDragGestures(
                onDragStart = {
                    totalDragX = 0f
                    totalDragY = 0f
                },
                onDragEnd = {
                    if (onHorizontalSwipe != null &&
                        kotlin.math.abs(totalDragX) > 130f &&
                        kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) * 1.35f
                    ) {
                        // Swipe left (< 0) -> next chapter (+1); Swipe right (> 0) -> prev chapter (-1)
                        onHorizontalSwipe(if (totalDragX < 0f) 1 else -1)
                    }
                    touchTiltX = 0f
                    touchTiltY = 0f
                    totalDragX = 0f
                    totalDragY = 0f
                },
                onDragCancel = {
                    touchTiltX = 0f
                    touchTiltY = 0f
                    totalDragX = 0f
                    totalDragY = 0f
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    totalDragX += dragAmount.x
                    totalDragY += dragAmount.y
                    touchTiltY = (touchTiltY + dragAmount.x * 0.14f).coerceIn(-maxTiltDeg, maxTiltDeg)
                    touchTiltX = (touchTiltX - dragAmount.y * 0.14f).coerceIn(-maxTiltDeg, maxTiltDeg)
                }
            )
        }
    } else {
        Modifier
    }

    this
        .then(pointerMod)
        .graphicsLayer {
            rotationX = animRotX
            rotationY = animRotY
            cameraDistance = 14f * density
            shadowElevation = 10.dp.toPx()
        }
}

private const val GOLD_AGSL_SRC = """
uniform float2 iResolution;
uniform float iTime;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / max(iResolution, float2(1.0));
    float sweep = sin((uv.x * 3.2 - uv.y * 1.4 - iTime * 1.6) * 3.14159);
    float glint = smoothstep(0.55, 1.0, sweep);
    half3 baseGold = half3(0.831, 0.686, 0.216);
    half3 deepGold = half3(0.710, 0.545, 0.120);
    half3 brightGold = half3(0.975, 0.885, 0.560);
    half3 col = mix(deepGold, baseGold, half(uv.x * 0.5 + 0.5));
    col = mix(col, brightGold, half(glint * 0.65));
    return half4(col, 1.0);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun rememberAgslGoldBrush(phase: Float): Brush {
    val shader = remember { RuntimeShader(GOLD_AGSL_SRC) }
    return remember(phase) {
        shader.setFloatUniform("iResolution", 600f, 180f)
        shader.setFloatUniform("iTime", phase)
        ShaderBrush(shader)
    }
}

/**
 * Animated metallic gold Brush: uses AGSL RuntimeShader on Android 13+ (API 33+)
 * and smooth animated linearGradient fallback on API 29-32. Static when reduced effects are on.
 */
@Composable
fun rememberMetallicGoldBrush(animated: Boolean = true): Brush {
    val reduceEffects = LocalLuxReduceEffects.current
    if (reduceEffects || !animated) {
        return remember {
            Brush.linearGradient(listOf(Color(0xFFC59B27), LuxGoldAccent, LuxGoldBright))
        }
    }

    val transition = rememberInfiniteTransition(label = "luxGoldShimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "goldPhase"
    )

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberAgslGoldBrush(phase)
    } else {
        val shift = phase * 260f
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFB58E1E),
                LuxGoldAccent,
                LuxGoldBright,
                LuxGoldAccent,
                Color(0xFFB58E1E)
            ),
            start = Offset(shift - 220f, 0f),
            end = Offset(shift + 220f, 120f)
        )
    }
}

/**
 * Primary CTA button with animated metallic gold surface shimmer (without blocking text or resizing).
 */
@Composable
fun LuxGoldButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val goldBrush = rememberMetallicGoldBrush(animated = enabled)
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .clip(shape)
            .then(
                if (enabled) Modifier.background(goldBrush, shape) else Modifier
            )
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().let {
                // Respect standard padding inside gradient surface
                it
            },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * Animated metallic shimmer progress bar for active conversions, translations, and downloads.
 */
@Composable
fun LuxAnimatedProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    val clamped = progress.coerceIn(0f, 1f)
    val goldBrush = rememberMetallicGoldBrush(animated = !isError && clamped > 0f && clamped < 1f)
    val trackColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(trackColor)
    ) {
        if (clamped > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(clamped)
                    .clip(RoundedCornerShape(3.dp))
                    .then(
                        if (isError) Modifier.background(MaterialTheme.colorScheme.error)
                        else Modifier.background(goldBrush)
                    )
            )
        }
    }
}

/**
 * Animated version of current ic_playlux_logo with metallic gold/silver sweep and subtle depth parallax.
 */
@Composable
fun AnimatedPlayLuxLogo(
    size: Dp,
    modifier: Modifier = Modifier,
    enableTilt: Boolean = true
) {
    val reduceEffects = LocalLuxReduceEffects.current
    val gyro by rememberGyroParallax(enabled = enableTilt && !reduceEffects)
    val transition = rememberInfiniteTransition(label = "logoMetallic")
    val sheenOffset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "logoSheen"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                if (!reduceEffects && enableTilt) {
                    rotationX = (gyro.y * 8f).coerceIn(-8f, 8f)
                    rotationY = (gyro.x * 8f).coerceIn(-8f, 8f)
                    cameraDistance = 12f * density
                }
            }
            .clip(CircleShape)
            .border(1.5.dp, LuxGoldAccent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_playlux_logo),
            contentDescription = "Play LuX Logo",
            modifier = Modifier.fillMaxSize()
        )
        if (!reduceEffects) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val x = sheenOffset * this.size.width
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            LuxGoldBright.copy(alpha = 0.24f),
                            LuxSilverBright.copy(alpha = 0.30f),
                            Color.Transparent
                        ),
                        start = Offset(x - this.size.width * 0.45f, 0f),
                        end = Offset(x + this.size.width * 0.45f, this.size.height)
                    )
                )
            }
        }
    }
}

/**
 * Brief (max 1.5s) animated launch overlay showing depth + metallic shimmer Play LuX logo.
 */
@Composable
fun LuxSplashOverlay(
    visible: Boolean,
    onFinished: () -> Unit
) {
    val reduceEffects = LocalLuxReduceEffects.current

    LaunchedEffect(visible, reduceEffects) {
        if (visible) {
            if (reduceEffects) {
                onFinished()
            } else {
                // Strictly <= 1.5s total launch splash
                delay(1150L)
                onFinished()
            }
        }
    }

    AnimatedVisibility(
        visible = visible && !reduceEffects,
        exit = fadeOut(animationSpec = tween(durationMillis = 280))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF2B194D), LuxDeepPurple)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedPlayLuxLogo(size = 112.dp, enableTilt = true)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Play LuX",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = LuxGoldBright,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Santuario de Audiolibros",
                    style = MaterialTheme.typography.labelMedium,
                    color = LuxSilverBright.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/**
 * Bundled Lottie indicator (res/raw/anim_lux_loader.json) for active conversion, translation & download states.
 */
@Composable
fun LuxProcessingLottie(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val reduceEffects = LocalLuxReduceEffects.current
    if (reduceEffects) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(2.dp, LuxGoldAccent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(size * 0.4f)
                    .clip(CircleShape)
                    .background(LuxGoldBright)
            )
        }
        return
    }

    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.anim_lux_loader))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier.size(size)
    )
}
