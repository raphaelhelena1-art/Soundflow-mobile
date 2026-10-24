package com.soundflow.core.player

/** Un seul actif à la fois : activer un mode désactive automatiquement les autres. */
enum class PlaybackMode { SEQUENTIAL, SHUFFLE, REPEAT_ALL, REPEAT_ONE }
