package com.olerast.suflyor

import com.olerast.suflyor.doc.Paragraph
import com.olerast.suflyor.doc.ScriptDocument
import com.olerast.suflyor.script.ScriptLayout
import com.olerast.suflyor.script.SpeechLang
import com.olerast.suflyor.script.WordNorm
import com.olerast.suflyor.track.ScriptTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Indonesian: auto-detect, spelling variants, spoken numbers, and the tracker on recognizer-like text. */
class IndonesianTest {
    private val id = SpeechLang.ID

    private fun detect(text: String) = SpeechLang.detect(ScriptDocument("t", "test", text.split("\n").map { Paragraph(it) }))

    @Test
    fun detectsIndonesianByFunctionWords() {
        assertEquals(id, detect("Halo semuanya, hari ini saya akan menunjukkan bagaimana teleprompter ini mengikuti suara Anda dan menggulir teks dengan sendirinya."))
        assertEquals(id, detect("Kalau kamu salah ucap dan mengulang kalimat dari awal, teks akan kembali ke awal kalimat itu."))
        // Other Latin-script languages and English stay as they were.
        assertEquals(SpeechLang.EN, detect("This is a simple English teleprompter script for testing."))
        assertEquals(SpeechLang.IT, detect("Là onde il re ordinò che tutti i medici della città venissero alla sua presenza e liberamente dicessero."))
        assertEquals(SpeechLang.PT, detect("A cabeça ficou segura na carne da vítima e das artérias rotas jorrava o sangue. Não havia mais cães a matar."))
        // Too short to tell: English, as before.
        assertEquals(SpeechLang.EN, detect("Halo!"))
    }

    @Test
    fun normalizesCasualSpellingsToOneWord() {
        val n = WordNorm.ID
        // The model prints UPPER CASE words; the script has any spelling of the same spoken word.
        assertEquals(listOf("saya", "nggak", "tahu"), n.words("SAYA ENGGAK TAU"))
        assertEquals(listOf("saya", "nggak", "tahu"), n.words("Saya gak tahu."))
        assertEquals(n.normalizeWord("gimana"), n.normalizeWord("Bagaimana"))
        assertEquals(n.normalizeWord("udah"), n.normalizeWord("SUDAH"))
        // Reduplication splits at the hyphen, as the recognizer's own "anak anak" does.
        assertEquals(listOf("anak", "anak", "itu"), n.words("Anak-anak itu"))
        assertEquals(listOf("anak", "anak", "itu"), n.words("ANAK ANAK ITU"))
    }

    @Test
    fun comparesWordFormsAndWeights() {
        val n = WordNorm.ID
        assertTrue(n.similarity("bukunya", "buku") >= 0.85f)
        assertTrue(n.similarity("meja", "mata") < 0.6f)
        // Function words prove little, long content words a lot.
        assertEquals(0.2f, n.weight("yang", false), 0f)
        assertEquals(1f, n.weight("teleprompter", false), 0f)
        assertEquals(0f, n.weight("2025", false), 0f)
        // "membaca" and "dibaca" group as one word for the frequency weights.
        assertEquals(n.stem("membaca"), n.stem("dibaca"))
        assertFalse(n.canBePrefix("di"))
        assertTrue(n.isAbbreviation("Dr."))
        assertTrue(n.isAbbreviation("dll."))
        assertFalse(n.isAbbreviation("rumah."))
    }

    @Test
    fun dropsSpokenNumbersWhenTheScriptHasDigits() {
        val n = WordNorm.ID
        val vocab = setOf("pada", "tahun", "kami", "pindah")
        // The model has no digits in its vocabulary: "2025" is heard as words.
        assertEquals(
            listOf("pada", "tahun", "kami", "pindah"),
            n.prepareHypothesis(listOf("pada", "tahun", "dua", "ribu", "dua", "puluh", "lima", "kami", "pindah"), vocab, true),
        )
        // Without digits in the script the words stay, as they are part of it.
        assertEquals(
            listOf("dua", "puluh", "lima"),
            n.prepareHypothesis(listOf("dua", "puluh", "lima"), setOf("dua", "puluh", "lima"), false),
        )
    }

    @Test
    fun tracksIndonesianWithRecognizerNoise() {
        val lines = listOf(
            "Halo semuanya, hari ini saya akan menunjukkan bagaimana teleprompter ini bekerja.",
            "Bacalah beberapa baris, lihat ke kamera, lalu lirik kembali ke teks.",
            "Kalau kamu salah ucap dan mengulang kalimat dari awal, teks akan kembali ke sana.",
            "Pada tahun 2025 kami sudah memakai aplikasi ini untuk membuat ratusan video.",
            "Jangan khawatir, teks hanya menunggu sampai kamu siap melanjutkan.",
        )
        val model = ScriptLayout.build(ScriptDocument("t", "test", lines.map { Paragraph(it) }), phraseMode = true, lang = id)
        val tracker = ScriptTracker(model.tokens, model.lang)
        val rnd = Random(11)
        // What the model prints for this text: casual spellings, spoken numbers, a word split in two, a misheard word.
        val spoken = id.words(
            "halo semuanya hari ini saya akan menunjukkan gimana tele prompter ini bekerja " +
                "bacalah beberapa baris lihat ke kamera lalu lirik kembali ke teks " +
                "kalo kamu salah ucap dan mengulang kalimat dari awal teks akan kembali ke sana " +
                "pada tahun dua ribu dua puluh lima kami udah memakai aplikasi ini untuk membuat ratusan video " +
                "jangan khawatir teks hanya menunggu sampai kamu siap melanjutkan",
        )
        val heard = mutableListOf<String>()
        for (w in spoken) {
            heard += w
            if (rnd.nextInt(10) == 0) heard += "eee"
            // The tracker drops fillers, joins split words and skips spoken numbers itself.
            tracker.onHypothesis(heard.takeLast(id.history))
        }
        val total = model.spokenTokens
        assertTrue("position ${tracker.position} of $total", tracker.position >= total - 2)
    }
}
