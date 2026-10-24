package com.soundflow.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView

/** Wrapper Compose autour de PlayerView (Media3) — affiche le rendu vidéo du lecteur en cours. */
@Composable
fun VideoSurface(player: Player?, useController: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = {
            PlayerView(context).apply {
                this.useController = useController
                this.player = player
            }
        },
        update = { view ->
            view.player = player
            view.useController = useController
        },
    )
}
