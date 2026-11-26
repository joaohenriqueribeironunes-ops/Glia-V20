package com.glia.assistant.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class NeuralVoiceAssetTest {

    private fun asset(): File = File("app/src/main/assets/${NeuralVoiceLayout.ASSET_NAME}")

    @Test
    fun assetEmbutidoExisteETemHashConhecido() {
        val zip = asset()
        assertTrue("asset da voz neural ausente: ${zip.absolutePath}", zip.isFile)
        assertEquals(58_800_891L, zip.length())
        assertEquals(NeuralVoiceLayout.ZIP_SHA256, NeuralVoiceLayout.sha256(zip))
    }

    @Test
    fun zipECompletoESeguro() {
        ZipFile(asset()).use {
            assertNull(NeuralVoiceLayout.validateZip(it))
            assertEquals(NeuralVoiceLayout.EXPECTED_ENTRY_COUNT, it.entries().toList().size)
            assertEquals(NeuralVoiceLayout.EXPECTED_UNCOMPRESSED_BYTES, NeuralVoiceLayout.uncompressedBytes(it))
            fun entrySize(relative: String): Long = it.entries().toList()
                .first { entry -> NeuralVoiceLayout.safeRelative(entry.name) == relative }
                .size
            assertTrue(entrySize(NeuralVoiceLayout.MODEL_NAME) > 0L)
            assertTrue(entrySize("espeak-ng-data/phondata") > 0L)
        }
    }

    @Test
    fun entradaComPathTraversalEhRejeitada() {
        assertEquals(
            "README",
            NeuralVoiceLayout.safeRelative("glia-neural-voice/README")
        )
        assertNull(NeuralVoiceLayout.safeRelative("glia-neural-voice/../segredo"))
        assertNull(NeuralVoiceLayout.safeRelative("/glia-neural-voice/README"))
        assertEquals("README", NeuralVoiceLayout.safeRelative("glia-neural-voice\\README"))
        assertNull(NeuralVoiceLayout.safeRelative("outro-modelo/README"))
        assertNull(NeuralVoiceLayout.safeRelative("C:/glia-neural-voice/README"))
    }

    @Test
    fun zipMalignoEhRejeitadoPelaValidacao() {
        val dir = Files.createTempDirectory("glia-neural-malicious-").toFile()
        val zipFile = File(dir, "bad.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(ZipEntry("glia-neural-voice/../../segredo"))
            zip.write("vazamento".toByteArray())
            zip.closeEntry()
        }
        ZipFile(zipFile).use { zip ->
            assertNotNullMessage(NeuralVoiceLayout.validateZip(zip))
        }
    }

    @Test
    fun diretorioValidoExigeTodosOsArquivosObrigatorios() {
        val dir = Files.createTempDirectory("glia-neural-dir-").toFile()
        NeuralVoiceLayout.REQUIRED_FILES.forEach { relative ->
            val file = File(dir, relative)
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { it.write(byteArrayOf(1, 2, 3)) }
        }
        assertTrue(NeuralVoiceLayout.isValidModelDir(dir))
        assertTrue(File(dir, NeuralVoiceLayout.MODEL_NAME).delete())
        assertFalse(NeuralVoiceLayout.isValidModelDir(dir))
        assertFalse(NeuralVoiceLayout.isValidModelDir(File(dir, "inexistente")))
    }

    private fun assertNotNullMessage(value: String?) {
        assertTrue("validação deveria apontar problema", value != null && value.isNotBlank())
    }
}
