package com.soundflow.core.extraction

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import okhttp3.Request as OkHttpRequest

/**
 * NewPipeExtractor ne fait aucune requête réseau lui-même : il délègue à un Downloader
 * fourni par l'app. On implémente ce pont avec OkHttp.
 */
class SoundFlowDownloader(private val client: OkHttpClient) : Downloader() {

    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val dataToSend = request.dataToSend()
        val requestBody = dataToSend?.toRequestBody()

        val builder = OkHttpRequest.Builder()
            .method(request.httpMethod(), requestBody)
            .url(request.url())
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            // Contourne la page de consentement cookies que YouTube affiche aux utilisateurs
            // situés dans l'UE. Sans ça, on reçoit une page de consentement au lieu du vrai
            // contenu, et l'extraction échoue avec des erreurs peu claires ("page needs to reload").
            .header("Cookie", "CONSENT=YES+cb.20210328-17-p0.en+FX+678")

        for ((headerName, headerValues) in request.headers()) {
            if (headerValues.size > 1) {
                builder.removeHeader(headerName)
                headerValues.forEach { builder.addHeader(headerName, it) }
            } else if (headerValues.size == 1) {
                builder.header(headerName, headerValues[0])
            }
        }

        val response = client.newCall(builder.build()).execute()

        if (response.code == 429) {
            response.close()
            throw IOException("Trop de requêtes envoyées (429) — réessaie dans un instant.")
        }

        val responseBodyString = response.body?.string().orEmpty()
        val latestUrl = response.request.url.toString()

        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBodyString,
            latestUrl,
        )
    }
}
