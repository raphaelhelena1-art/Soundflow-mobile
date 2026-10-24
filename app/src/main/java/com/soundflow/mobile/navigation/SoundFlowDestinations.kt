package com.soundflow.mobile.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.soundflow.mobile.R

// Ordre demandé : Accueil, Recherche, Bibliothèque, Téléchargements, Profil.
// "Bibliothèque" contient désormais les Playlists (dont Favoris, épinglée en haut) —
// l'ancien historique d'écoute a été retiré au profit de cette vue.
enum class SoundFlowDestination(val route: String, val labelResId: Int, val icon: ImageVector) {
    HOME("home", R.string.nav_home, Icons.Filled.Home),
    SEARCH("search", R.string.nav_search, Icons.Filled.Search),
    LIBRARY("library", R.string.nav_library, Icons.Filled.LibraryMusic),
    DOWNLOADS("downloads", R.string.nav_downloads, Icons.Filled.CloudDownload),
    PROFILE("profile", R.string.nav_profile, Icons.Filled.Person),
}
