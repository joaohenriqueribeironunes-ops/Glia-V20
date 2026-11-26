package com.glia.assistant.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class NeuralVoiceStoreTest {

    private fun tempDir(): File = Files.createTempDirectory("glia-neural-extract-").toFile()

    private fun assetZip(): File = File("app/src/main/assets/${NeuralVoiceLayout.ASSET_NAME}")

    @Test
    fun extraiModeloNeuralRealCompleto() {
        val dir = tempDir()
        val zip = File(dir, "voice.zip")
        assetZip().copyTo(zip)
        val target = File(dir, NeuralVoiceLayout.DIR_NAME)

        val result = NeuralVoiceStore.extractFromZip(zip, target)

        assertNotNull(result)
        assertTrue(NeuralVoiceLayout.isValidModelDir(target))
        val total = target.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        assertEquals(NeuralVoiceLayout.EXPECTED_UNCOMPRESSED_BYTES, total)
        assertFalse(File(dir, "${NeuralVoiceLayout.DIR_NAME}.extract").exists())
    }

    @Test
    fun extracaoEIdempotente() {
        val dir = tempDir()
        val zip = File(dir, "voice.zip")
        assetZip().copyTo(zip)
        val target = File(dir, NeuralVoiceLayout.DIR_NAME)

        val first = NeuralVoiceStore.extractFromZip(zip, target)
        val second = NeuralVoiceStore.extractFromZip(zip, target)

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(first!!.absolutePath, second!!.absolutePath)
        assertTrue(NeuralVoiceLayout.isValidModelDir(second))
    }

    @Test
    fun zipComHashAlteradoNaoPublicaModelo() {
        val dir = tempDir()
        val zip = File(dir, "voice.zip")
        assetZip().copyTo(zip)
        val bytes = zip.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0xFF).toByte()
        zip.writeBytes(bytes)
        val target = File(dir, NeuralVoiceLayout.DIR_NAME)

        assertNull(NeuralVoiceStore.extractFromZip(zip, target))
        assertFalse(target.exists())
        assertFalse(File(dir, "${NeuralVoiceLayout.DIR_NAME}.extract").exists())
    }

    @Test
    fun zipMalignoNaoPublicaModelo() {
        val dir = tempDir()
        val zip = File(dir, "malicious.zip")
        ZipOutputStream(FileOutputStream(zip)).use { out ->
            out.putNextEntry(ZipEntry("glia-neural-voice/../../segredo"))
            out.write("vazamento".toByteArray())
            out.closeEntry()
        }
        val target = File(dir, NeuralVoiceLayout.DIR_NAME)

        assertNull(NeuralVoiceStore.extractFromZip(zip, target))
        assertFalse(target.exists())
        assertFalse(File(dir, "segredo").exists())
    }

    @Test
    fun alvoInvalidoESubstituidoPorExtracaoValida() {
        val dir = tempDir()
        val zip = File(dir, "voice.zip")
        assetZip().copyTo(zip)
        val target = File(dir, NeuralVoiceLayout.DIR_NAME)
        target.mkdirs()
        FileOutputStream(File(target, NeuralVoiceLayout.MODEL_NAME)).use { it.write(byteArrayOf(1)) }

        val result = NeuralVoiceStore.extractFromZip(zip, target)

        assertNotNull(result)
        assertTrue(NeuralVoiceLayout.isValidModelDir(target))
        assertEquals(
            NeuralVoiceLayout.EXPECTED_UNCOMPRESSED_BYTES,
            target.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        )
    }

    @Test
    fun zipInexistenteRetornaNullSemCriarDestino() {
        val dir = tempDir()
        val target = File(dir, NeuralVoiceLayout.DIR_NAME)
        assertNull(NeuralVoiceStore.extractFromZip(File(dir, "missing.zip"), target))
        assertFalse(target.exists())
    }
}
