package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.di.AppContainer
import com.example.ui.components.MiniPlayer
import com.example.ui.converter.ConverterScreen
import com.example.ui.effects.AnimatedPlayLuxLogo
import com.example.ui.effects.LocalLuxReduceEffects
import com.example.ui.effects.rememberMetallicGoldBrush
import com.example.ui.navigation.Screen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.player.PlayerScreen
import com.example.ui.player.PlayerViewModel
import com.example.ui.playlists.PlaylistsScreen
import com.example.ui.search.SearchDownloadScreen
import com.example.ui.settings.RecycleScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.LuxGoldAccent
import com.example.ui.theme.LuxGoldBright
import com.example.ui.theme.LuxSilverBright
import com.example.ui.theme.LuxSilverText
import com.example.ui.translator.TranslatorScreen
import com.example.ui.vault.BookDetailScreen
import com.example.ui.vault.VaultScreen
import com.example.ui.vault.VaultViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun MainRootScreen(
    appContainer: AppContainer,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: startDestination

    val playbackState by appContainer.playbackConnection.uiState.collectAsStateWithLifecycle()
    var pendingTranslatorTextId by remember { mutableStateOf<String?>(null) }
    var pendingConverterTextId by remember { mutableStateOf<String?>(null) }

    val navigateToModule = { route: String ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val moduleRoutes = listOf(
        Screen.Vault.route,
        Screen.Playlists.route,
        Screen.Search.route,
        Screen.Translator.route,
        Screen.Converter.route
    )
    val isModuleScreen = currentRoute in moduleRoutes

    // Mini-player is hidden in full-screen player, in onboarding,
    // and in BookDetail if the currently playing book is the one being viewed
    val detailBookId = if (currentRoute.startsWith("book_detail/")) {
        navBackStackEntry?.arguments?.getString("bookId")
    } else null
    val hideMiniPlayer = currentRoute == Screen.Player.route ||
            currentRoute == Screen.Onboarding.route ||
            (detailBookId != null && playbackState.book?.id == detailBookId)

    val topNavModules = listOf(
        TopModuleItem(Screen.Vault.route, stringResource(R.string.nav_vault), Icons.AutoMirrored.Filled.MenuBook, "top_nav_vault"),
        TopModuleItem(Screen.Playlists.route, stringResource(R.string.nav_playlists), Icons.AutoMirrored.Filled.PlaylistPlay, "top_nav_playlists"),
        TopModuleItem(Screen.Search.route, stringResource(R.string.nav_search), Icons.Default.CloudDownload, "top_nav_search"),
        TopModuleItem(Screen.Translator.route, stringResource(R.string.nav_translator), Icons.Default.Translate, "top_nav_translator"),
        TopModuleItem(Screen.Converter.route, stringResource(R.string.nav_converter), Icons.Default.RecordVoiceOver, "top_nav_converter")
    )

    Scaffold(
        topBar = {
            // ÚNICA barra superior metálica de 56 dp debajo de la barra de estado, sin barra negra del sistema
            if (isModuleScreen) {
                val reduceEffects = LocalLuxReduceEffects.current
                val metallicGoldBrush = rememberMetallicGoldBrush(animated = !reduceEffects)
                val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                val barGradient = if (isDark) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF1A0F33),
                            Color(0xFF2B1B4E),
                            Color(0xFF21143F)
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                }
                val brandBorderBrush = Brush.linearGradient(
                    colors = listOf(
                        LuxGoldBright.copy(alpha = 0.75f),
                        LuxSilverBright.copy(alpha = 0.55f),
                        LuxGoldAccent.copy(alpha = 0.75f)
                    )
                )

                Surface(
                    color = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(barGradient)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Logo Play LuX integrado con título metálico en una sola insignia
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(start = 4.dp, end = 4.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isDark) Color(0xFF33205D).copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .border(0.8.dp, brandBorderBrush, RoundedCornerShape(14.dp))
                                    .clickable {
                                        navController.navigate(Screen.Vault.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    .padding(start = 4.dp, end = 7.dp, top = 4.dp, bottom = 4.dp)
                                    .testTag("app_logo_emblem")
                            ) {
                                AnimatedPlayLuxLogo(
                                    size = 24.dp,
                                    enableTilt = false
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Play",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) LuxSilverBright else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "LuX",
                                    style = TextStyle(
                                        brush = metallicGoldBrush,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.4.sp
                                    ),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .width(1.dp)
                                    .height(22.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                LuxGoldBright.copy(alpha = 0.45f),
                                                LuxSilverBright.copy(alpha = 0.35f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )

                            // 5 Módulos de navegación: iconos metálicos plata/oro y etiqueta solo en el seleccionado
                            topNavModules.forEach { item ->
                                val selected = currentRoute == item.route
                                val tabScale by animateFloatAsState(
                                    targetValue = if (selected && !reduceEffects) 1.04f else 1f,
                                    animationSpec = spring(stiffness = 380f, dampingRatio = 0.72f),
                                    label = "topNavScale"
                                )
                                Row(
                                    modifier = Modifier
                                        .weight(if (selected) 1.65f else 0.95f)
                                        .fillMaxHeight()
                                        .padding(vertical = 8.dp, horizontal = 2.dp)
                                        .graphicsLayer {
                                            scaleX = tabScale
                                            scaleY = tabScale
                                        }
                                        .clip(RoundedCornerShape(10.dp))
                                        .then(
                                            if (selected) {
                                                Modifier
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            listOf(
                                                                LuxGoldAccent.copy(alpha = 0.24f),
                                                                LuxGoldBright.copy(alpha = 0.14f)
                                                            )
                                                        )
                                                    )
                                                    .border(
                                                        0.8.dp,
                                                        Brush.linearGradient(
                                                            listOf(
                                                                LuxGoldBright.copy(alpha = 0.7f),
                                                                LuxSilverBright.copy(alpha = 0.45f)
                                                            )
                                                        ),
                                                        RoundedCornerShape(10.dp)
                                                    )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .clickable {
                                            if (!selected) {
                                                navController.navigate(item.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        }
                                        .padding(horizontal = 4.dp)
                                        .testTag(item.testTag),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label,
                                        tint = if (selected) LuxGoldBright else LuxSilverBright.copy(alpha = 0.85f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    // Etiqueta SOLO en el módulo seleccionado
                                    if (selected) {
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = item.label,
                                            style = TextStyle(
                                                brush = metallicGoldBrush,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.5.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Botón ⋮ metálico a la derecha que abre Ajustes
                            IconButton(
                                onClick = { navController.navigate(Screen.Settings.route) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .border(0.6.dp, LuxGoldAccent.copy(alpha = 0.35f), CircleShape)
                                    .testTag("top_menu_settings_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.settings_title),
                                    tint = LuxGoldBright,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            LuxGoldAccent.copy(alpha = 0.55f),
                                            LuxSilverBright.copy(alpha = 0.45f),
                                            LuxGoldBright.copy(alpha = 0.65f),
                                            LuxSilverBright.copy(alpha = 0.45f),
                                            LuxGoldAccent.copy(alpha = 0.55f)
                                        )
                                    )
                                )
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Único elemento fijo inferior: mini-player (56 dp), con padding de barra de navegación
            AnimatedVisibility(
                visible = !hideMiniPlayer && playbackState.book != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                Box(modifier = Modifier.navigationBarsPadding()) {
                    MiniPlayer(
                        uiState = playbackState,
                        onTogglePlay = { appContainer.playbackConnection.togglePlayPause() },
                        onRewindJump = {
                            appContainer.playbackConnection.seekBy(-(playbackState.jumpSeconds * 1000L))
                        },
                        onOpenPlayer = { navController.navigate(Screen.Player.route) },
                        onPreviousChapter = { appContainer.playbackConnection.previousChapter() },
                        onNextChapter = { appContainer.playbackConnection.nextChapter() },
                        onDismissStop = { appContainer.playbackConnection.stopPlayback() }
                    )
                }
            }
        },
        modifier = modifier
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onFinishOnboarding = {
                        CoroutineScope(Dispatchers.Main).launch {
                            appContainer.settingsStore.setOnboardingCompleted(true)
                            navController.navigate(Screen.Vault.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(Screen.Vault.route) {
                val vaultViewModel: VaultViewModel = viewModel(
                    factory = VaultViewModel.provideFactory(
                        bookRepository = appContainer.bookRepository,
                        folderRepository = appContainer.folderRepository,
                        recycleRepository = appContainer.recycleRepository,
                        textItemRepository = appContainer.textItemRepository,
                        settingsStore = appContainer.settingsStore,
                        playbackConnection = appContainer.playbackConnection
                    )
                )
                VaultScreen(
                    viewModel = vaultViewModel,
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onOpenBookDetail = { bookId -> navController.navigate(Screen.BookDetail.createRoute(bookId)) },
                    onNavigateToTranslator = { textId ->
                        pendingTranslatorTextId = textId
                        navigateToModule(Screen.Translator.route)
                    },
                    onNavigateToConverter = { textId ->
                        pendingConverterTextId = textId
                        navigateToModule(Screen.Converter.route)
                    }
                )
            }

            composable(Screen.Playlists.route) {
                PlaylistsScreen(
                    playlistRepository = appContainer.playlistRepository,
                    bookRepository = appContainer.bookRepository,
                    playbackConnection = appContainer.playbackConnection,
                    onOpenPlayer = { navController.navigate(Screen.Player.route) }
                )
            }

            composable(Screen.Search.route) {
                SearchDownloadScreen(
                    searchEngine = appContainer.publicBookSearchEngine,
                    onOpenVaultTexts = { navigateToModule(Screen.Vault.route) }
                )
            }

            composable(Screen.Translator.route) {
                TranslatorScreen(
                    translatorEngine = appContainer.localTranslatorEngine,
                    textItemRepository = appContainer.textItemRepository,
                    initialTextId = pendingTranslatorTextId,
                    onConvertTranslatedText = { newTextId ->
                        pendingConverterTextId = newTextId
                        navigateToModule(Screen.Converter.route)
                    },
                    onOpenVaultTexts = { navigateToModule(Screen.Vault.route) }
                )
            }

            composable(Screen.Converter.route) {
                ConverterScreen(
                    ttsEngine = appContainer.ttsConverterEngine,
                    textItemRepository = appContainer.textItemRepository,
                    settingsStore = appContainer.settingsStore,
                    initialTextId = pendingConverterTextId,
                    onOpenBookDetail = { bookId -> navController.navigate(Screen.BookDetail.createRoute(bookId)) },
                    onOpenVaultAudiobooks = { navigateToModule(Screen.Vault.route) }
                )
            }

            composable(Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.provideFactory(
                        settingsStore = appContainer.settingsStore,
                        recycleRepository = appContainer.recycleRepository,
                        bookRepository = appContainer.bookRepository
                    )
                )
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToRecycle = { navController.navigate(Screen.Recycle.route) },
                    onNavigateToIntroduction = { navController.navigate(Screen.Onboarding.route) }
                )
            }

            composable(Screen.Recycle.route) {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.provideFactory(
                        settingsStore = appContainer.settingsStore,
                        recycleRepository = appContainer.recycleRepository,
                        bookRepository = appContainer.bookRepository
                    )
                )
                RecycleScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.BookDetail.route,
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
                BookDetailScreen(
                    bookId = bookId,
                    bookRepository = appContainer.bookRepository,
                    playbackConnection = appContainer.playbackConnection,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenPlayer = { navController.navigate(Screen.Player.route) }
                )
            }

            composable(Screen.Player.route) {
                val context = LocalContext.current
                val playerViewModel: PlayerViewModel = viewModel(
                    factory = PlayerViewModel.provideFactory(
                        context = context,
                        playbackConnection = appContainer.playbackConnection,
                        bookRepository = appContainer.bookRepository,
                        textItemRepository = appContainer.textItemRepository
                    )
                )
                PlayerScreen(
                    viewModel = playerViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

private data class TopModuleItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)
