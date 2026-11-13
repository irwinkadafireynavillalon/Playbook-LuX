package com.example.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.effects.LuxGoldButton
import com.example.ui.settings.openBatteryOptimizationSettings
import com.example.ui.theme.LuxGoldAccent
import com.example.ui.theme.LuxSilverBright
import com.example.ui.theme.LuxSilverText
import kotlinx.coroutines.launch

data class OnboardingPageData(
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector
)

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pages = listOf(
        OnboardingPageData(R.string.onboarding_page1_title, R.string.onboarding_page1_desc, Icons.Default.Headphones),
        OnboardingPageData(R.string.onboarding_page2_title, R.string.onboarding_page2_desc, Icons.Default.Folder),
        OnboardingPageData(R.string.onboarding_page3_title, R.string.onboarding_page3_desc, Icons.Default.RecordVoiceOver),
        OnboardingPageData(R.string.onboarding_page4_title, R.string.onboarding_page4_desc, Icons.Default.Notifications)
    )

    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    var notificationPermissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationPermissionGranted = isGranted
    }
    val isBatteryUnrestricted = remember(context) {
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top skip button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onFinishOnboarding,
                modifier = Modifier.testTag("onboarding_skip_btn")
            ) {
                Text(stringResource(R.string.onboarding_skip_btn), color = LuxSilverText)
            }
        }

        // Pager Section (takes center)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { pageIndex ->
            val page = pages[pageIndex]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Emblem
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(2.dp, LuxGoldAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = page.icon,
                        contentDescription = null,
                        tint = LuxGoldAccent,
                        modifier = Modifier.size(46.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(page.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = LuxSilverBright,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(page.descRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LuxSilverText,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.widthIn(max = 400.dp)
                )

                // Extra controls on page 4 (Permisos y batería)
                if (pageIndex == 3) {
                    Spacer(modifier = Modifier.height(20.dp))

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionGranted) {
                        OutlinedButton(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            modifier = Modifier.testTag("onboarding_notif_grant_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp), tint = LuxGoldAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.onboarding_notif_btn), color = LuxGoldAccent)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    OutlinedButton(
                        onClick = { openBatteryOptimizationSettings(context) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("onboarding_battery_btn")
                    ) {
                        Icon(
                            imageVector = if (isBatteryUnrestricted) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = LuxGoldAccent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBatteryUnrestricted) "Segundo plano permitido" else "Permitir segundo plano (batería)",
                            color = LuxGoldAccent
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = LuxGoldAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Opcional: desactiva la restricción de batería para que conversiones y reproducción sigan sin cortes.",
                                style = MaterialTheme.typography.labelSmall,
                                color = LuxSilverText
                            )
                        }
                    }
                }
            }
        }

        // Bottom Controls: Page Indicator & Action Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp)
        ) {
            // Indicator dots
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                repeat(pages.size) { iteration ->
                    val isCurrent = pagerState.currentPage == iteration
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(if (isCurrent) 10.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (isCurrent) LuxGoldAccent else LuxSilverText.copy(alpha = 0.4f))
                    )
                }
            }

            // Next / Start Button with metallic shimmer
            LuxGoldButton(
                onClick = {
                    if (pagerState.currentPage < pages.size - 1) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onFinishOnboarding()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("onboarding_finish_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (pagerState.currentPage < pages.size - 1) "Siguiente" else stringResource(R.string.onboarding_start_btn),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.surface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

