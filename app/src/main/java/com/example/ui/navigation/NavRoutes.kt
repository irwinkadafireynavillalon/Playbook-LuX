package com.example.ui.navigation

sealed class Screen(val route: String) {
    data object Vault : Screen("vault")
    data object Playlists : Screen("playlists")
    data object Search : Screen("search")
    data object Translator : Screen("translator")
    data object Converter : Screen("converter")

    data object Settings : Screen("settings")
    data object Recycle : Screen("recycle")
    data object Player : Screen("player")
    data object Onboarding : Screen("onboarding")
    data object BookDetail : Screen("book_detail/{bookId}") {
        fun createRoute(bookId: String): String = "book_detail/$bookId"
    }
}
