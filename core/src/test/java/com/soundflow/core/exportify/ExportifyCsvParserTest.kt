package com.soundflow.core.exportify

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportifyCsvParserTest {

    @Test
    fun testParseStandardExportifyCsv() {
        val csv = """
            Track URI,Track Name,Artist Name(s),Album Name,Album Artist Name(s),Album Release Date,Disc Number,Track Number,Track Duration (ms),Explicit,Popularity,Added By,Added At
            "spotify:track:4iV5W9uYEdYUVa79Axb7Rh","Blinding Lights","The Weeknd","After Hours","The Weeknd","2020-03-20",1,9,200040,false,90,"spotify","2020-03-21T00:00:00Z"
            "spotify:track:0VjDiOGVJ425W8StJFDSt2","Starboy","The Weeknd, Daft Punk","Starboy","The Weeknd","2016-11-25",1,1,230453,true,88,"spotify","2020-03-21T00:00:00Z"
        """.trimIndent()

        val result = ExportifyCsvParser.parseText(csv, "Mes Favoris")

        assertEquals("Mes Favoris", result.name)
        assertEquals(2, result.tracks.size)

        val track1 = result.tracks[0]
        assertEquals("Blinding Lights", track1.title)
        assertEquals("The Weeknd", track1.artist)
        assertEquals(200040L, track1.durationMs)
        assertEquals(200L, track1.durationSeconds)

        val track2 = result.tracks[1]
        assertEquals("Starboy", track2.title)
        assertEquals("The Weeknd, Daft Punk", track2.artist)
        assertEquals(230453L, track2.durationMs)
    }

    @Test
    fun testParseCsvLineWithQuotedCommas() {
        val line = """"123","Song, Title (Remix)","Artist 1, Artist 2","Album","Artist",2020,1,1,180000"""
        val parsed = ExportifyCsvParser.parseCsvLine(line)

        assertEquals(9, parsed.size)
        assertEquals("123", parsed[0])
        assertEquals("Song, Title (Remix)", parsed[1])
        assertEquals("Artist 1, Artist 2", parsed[2])
    }
}
