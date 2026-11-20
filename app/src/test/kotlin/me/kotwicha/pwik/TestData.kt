package me.kotwicha.pwik

import java.io.File
import me.kotwicha.pwik.core.importer.Feed
import me.kotwicha.pwik.core.importer.FeedBuilder
import me.kotwicha.pwik.core.importer.Gazetteer
import me.kotwicha.pwik.core.importer.ShapeBuilder
import me.kotwicha.pwik.core.map.MapData
import me.kotwicha.pwik.core.map.StreetGraph
import me.kotwicha.pwik.core.map.Streets
import me.kotwicha.pwik.core.pdf.Glyph
import me.kotwicha.pwik.core.pdf.ParsedDocument
import me.kotwicha.pwik.core.pdf.PdfPage
import me.kotwicha.pwik.core.pdf.TimetablePdfParser
import me.kotwicha.pwik.core.pdf.WordAssembler
import me.kotwicha.pwik.core.timetable.Network
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import org.junit.Assume.assumeTrue

object TestData {
    private fun asset(name: String) = File("src/main/assets/seed/$name")

    val legacyPdf = File("../data/Rozklad-27.06.2026.pdf")
    val currentPdf = File("../data/Rozklad-01.11.2026.pdf")

    fun pdfPages(file: File): List<PdfPage> = PDDocument.load(file.also { assumeTrue("brak ${file.path}", it.exists()) }).use { doc ->
        (1..doc.numberOfPages).map { n ->
            val glyphs = ArrayList<Glyph>()
            val stripper = object : PDFTextStripper() {
                override fun processTextPosition(text: TextPosition) {
                    glyphs.add(Glyph(text.unicode ?: "", text.xDirAdj, text.yDirAdj, text.widthDirAdj, text.fontSizeInPt))
                }
            }
            stripper.startPage = n
            stripper.endPage = n
            stripper.getText(doc)
            PdfPage(n, WordAssembler.assemble(glyphs))
        }
    }

    val legacyDocument: ParsedDocument by lazy { TimetablePdfParser().parse(pdfPages(legacyPdf)) }
    val document: ParsedDocument by lazy { TimetablePdfParser().parse(pdfPages(currentPdf)) }
    val gazetteer: Gazetteer by lazy { Gazetteer.decode(asset("gazetteer.json").readText()) }
    val map: MapData by lazy { MapData.decode(asset("map.bin").readBytes()) }
    val streets: Streets by lazy { Streets(map) }
    val walkGraph: StreetGraph by lazy { StreetGraph.build(map, walking = true) }
    val driveGraph: StreetGraph by lazy { StreetGraph.build(map, walking = false) }

    val feed: Feed by lazy {
        val raw = FeedBuilder(gazetteer, streets).build(document)
        val shapes = ShapeBuilder(driveGraph)
        val net = raw.toNetwork()
        raw.copy(patterns = raw.patterns.mapIndexed { i, p ->
            val (shape, breaks) = shapes.build(p.stops.indices.map { net.platformPoint(i, it) })
            p.copy(shape = shape, shapeBreaks = breaks)
        })
    }
    val network: Network by lazy { feed.toNetwork() }
}
