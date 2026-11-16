package com.soundflow.mobile.spotify

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SpotifyAuthChannel {
    private val _codeFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val codeFlow: SharedFlow<String> = _codeFlow.asSharedFlow()

    fun onCodeReceived(code: String) {
        _codeFlow.tryEmit(code)
    }
}
