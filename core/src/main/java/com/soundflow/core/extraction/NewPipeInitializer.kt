package com.soundflow.core.extraction

import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.TimeUnit

object NewPipeInitializer {
    @Volatile private var initialized = false

    fun init() {
        if (initialized) return
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        NewPipe.init(SoundFlowDownloader(client), Localization("fr", "FR"), ContentCountry("FR"))
        initialized = true
    }
}
