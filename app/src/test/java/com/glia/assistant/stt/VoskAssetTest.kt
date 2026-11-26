package com.glia.assistant.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VoskAssetTest {

    private fun asset(): File = File("app/src/main/assets/${VoskLayout.ASSET_NAME}")

    @Test
    fun assetEmbutidoExisteETemHashConhecido() {
        val zip = asset()
        assertTrue("asset Vosk ausente: ${zip.absolutePath}", zip.isFile)
        assertEquals(32_453_112L, zip.length())
        assertEquals(VoskLayout.ZIP_SHA256, VoskLayout.sha256(zip))
    }

    @Test
    fun zipECompletoESeguro() {
        val zip = asset()
        java.util.zip.ZipFile(zip).use {
            assertNull(VoskLayout.validateZip(it))
            assertEquals(VoskLayout.EXPECTED_ENTRY_COUNT, it.entries().toList().size)
            assertEquals(VoskLayout.EXPECTED_UNCOMPRESSED_BYTES, VoskLayout.uncompressedBytes(it))
            assertTrue(it.getEntry("${VoskLayout.DIR_NAME}/final.mdl").size.toLong() > 0L)
            assertTrue(it.getEntry("${VoskLayout.DIR_NAME}/ivector/final.ie").size.toLong() > 0L)
        }
    }

    @Test
    fun entradaComPathTraversalEhRejeitada() {
        assertEquals("README", VoskLayout.safeRelative("vosk-model-small-pt-0.3/README"))
        assertNull(VoskLayout.safeRelative("vosk-model-small-pt-0.3/../segredo"))
        assertNull(VoskLayout.safeRelative("/vosk-model-small-pt-0.3/README"))
        assertNull(VoskLayout.safeRelative("vosk-model-small-pt-0.3\\README"))
        assertNull(VoskLayout.safeRelative("outro-modelo/README"))
        assertNull(VoskLayout.safeRelative("C:/vosk-model-small-pt-0.3/README"))
    }

    @Test
    fun zipMalignoEhRejeitadoPelaValidacao() {
        val dir = Files.createTempDirectory("glia-vosk-malicious-").toFile()
        val zipFile = File(dir, "bad.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(ZipEntry("vosk-model-small-pt-0.3/../../segredo"))
            zip.write("vazamento".toByteArray())
            zip.closeEntry()
        }
        java.util.zip.ZipFile(zipFile).use { zip ->
            assertNotNull(VoskLayout.validateZip(zip))
        }
    }

    @Test
    fun diretorioValidoExigeTodosOsArquivosObrigatorios() {
        val dir = Files.createTempDirectory("glia-vosk-dir-").toFile()
        VoskLayout.REQUIRED_FILES.forEach { relative ->
            val file = File(dir, relative)
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { it.write(byteArrayOf(1, 2, 3)) }
        }
        assertTrue(VoskLayout.isValidModelDir(dir))
        assertTrue(File(dir, "final.mdl").delete())
        assertFalse(VoskLayout.isValidModelDir(dir))
        assertFalse(VoskLayout.isValidModelDir(File(dir, "inexistente")))
    }

    @Test
    fun extracaoRealDoModeloQuandoDisponivel() {
        val path = System.getProperty("glia.vosk.dir")
        assumeTrue("diretorio real nao informado (-Dglia.vosk.dir=...)", !path.isNullOrBlank())
        assertTrue(VoskLayout.isValidModelDir(File(path)))
    }
}
