package me.kotwicha.pwik.data.sync

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import me.kotwicha.pwik.core.pdf.Glyph
import me.kotwicha.pwik.core.pdf.PdfPage
import me.kotwicha.pwik.core.pdf.WordAssembler

object PdfText {
    @Volatile
    private var ready = false

    fun pages(context: Context, bytes: ByteArray): List<PdfPage> {
        if (!ready) {
            PDFBoxResourceLoader.init(context.applicationContext)
            ready = true
        }
        return PDDocument.load(bytes).use { doc ->
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
    }
}
