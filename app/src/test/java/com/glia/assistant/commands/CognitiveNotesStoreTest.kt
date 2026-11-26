package com.glia.assistant.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CognitiveNotesStoreTest {

    private fun tempStore(): Pair<CognitiveNotesStore, File> {
        val dir = Files.createTempDirectory("glia-notes-test").toFile()
        val file = File(dir, CognitiveNotesStore.FILE_NAME)
        return CognitiveNotesStore(file) to file
    }

    @Test
    fun armazenaELePensamentosEmOrdemRecente() {
        val (store, _) = tempStore()
        assertNotNull(store.add("primeiro pensamento", 1_000L))
        assertNotNull(store.add("segundo pensamento", 2_000L))

        val notes = store.all()
        assertEquals(2, notes.size)
        assertEquals("segundo pensamento", notes[0].text)
        assertEquals("primeiro pensamento", notes[1].text)
        assertEquals(2, store.count())
    }

    @Test
    fun textoVazioNaoCriaNota() {
        val (store, _) = tempStore()
        assertNull(store.add("   ", 1_000L))
        assertNull(store.add("", 2_000L))
        assertEquals(0, store.count())
    }

    @Test
    fun textoLongoEResumidoNoLimite() {
        val (store, _) = tempStore()
        val huge = "a".repeat(CognitiveNotesStore.MAX_NOTE_CHARS + 500)
        val note = store.add(huge, 1_000L)
        assertNotNull(note)
        assertEquals(CognitiveNotesStore.MAX_NOTE_CHARS, note!!.text.length)
    }

    @Test
    fun preservaAcentosQuebrasDeLinhaETabs() {
        val (store, _) = tempStore()
        val text = "Ação, coração e café.\nSegunda linha\tcom tab."
        store.add(text, 1_000L)
        assertEquals(text, store.all().first().text)
    }

    @Test
    fun apagaNotaEspecifica() {
        val (store, _) = tempStore()
        val first = store.add("manter", 1_000L)!!
        val second = store.add("apagar", 2_000L)!!
        assertTrue(store.delete(second.id))
        assertEquals(1, store.count())
        assertEquals(first.id, store.all().first().id)
        assertFalse(store.delete("inexistente"))
    }

    @Test
    fun limparRemoveTudo() {
        val (store, _) = tempStore()
        store.add("um", 1_000L)
        store.add("dois", 2_000L)
        store.clear()
        assertEquals(0, store.count())
        assertFalse(store.summary().contains("pensamento guardado"))
    }

    @Test
    fun resumoMostraTotalEUltimoPensamento() {
        val (store, _) = tempStore()
        store.add("comprar café", 1_000L)
        store.add("ligar para a mãe", 2_000L)
        val summary = store.summary()
        assertTrue(summary.contains("2 pensamentos guardados"))
        assertTrue(summary.contains("ligar para a mãe"))
    }

    @Test
    fun linhaCorrompidaEhIgnoradaSemPerderAsBoas() {
        val (store, file) = tempStore()
        store.add("nota boa", 1_000L)
        file.appendText("id-quebrado\tnao-e-numero\tzzz\n")
        val notes = store.all()
        assertEquals(1, notes.size)
        assertEquals("nota boa", notes.first().text)
    }
}
