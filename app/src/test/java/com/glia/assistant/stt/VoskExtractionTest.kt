package com.glia.assistant.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipOutputStream

class VoskExtractionTest {

    private fun tempDir(): File = Files.createTempDirectory("glia-vosk-extract-").toFile()

    private fun assetZip(): File = File("app/src/main/assets/${VoskLayout.ASSET_NAME}")

    @Test
    fun extraiModeloRealCompleto() {
        val dir = tempDir()
        val zip = File(dir, "model.zip")
        assetZip().copyTo(zip)
        val target = File(dir, VoskLayout.DIR_NAME)

        val result = VoskModelStore.extractFromZip(zip, target)

        assertNotNull(result)
        assertTrue(VoskLayout.isValidModelDir(target))
        val total = target.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        assertEquals(VoskLayout.EXPECTED_UNCOMPRESSED_BYTES, total)
        assertFalse(File(dir, "${VoskLayout.DIR_NAME}.extract").exists())
    }

    @Test
    fun extracaoEIdempotente() {
        val dir = tempDir()
        val zip = File(dir, "model.zip")
        assetZip().copyTo(zip)
        val target = File(dir, VoskLayout.DIR_NAME)

        val first = VoskModelStore.extractFromZip(zip, target)
        val second = VoskModelStore.extractFromZip(zip, target)

        assertNotNull(first)
        assertEquals(first!!.absolutePath, second!!.absolutePath)
        assertTrue(VoskLayout.isValidModelDir(second))
    }

    @Test
    fun zipComHashAlteradoNaoPublicaModelo() {
        val dir = tempDir()
        val zip = File(dir, "model.zip")
        assetZip().copyTo(zip)
        val bytes = zip.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0xFF).toByte()
        zip.writeBytes(bytes)
        val target = File(dir, VoskLayout.DIR_NAME)

        assertNull(VoskModelStore.extractFromZip(zip, target))
        assertFalse(target.exists())
        assertFalse(File(dir, "${VoskLayout.DIR_NAME}.extract").exists())
    }

    @Test
    fun zipMalignoNaoPublicaModelo() {
        val dir = tempDir()
        val zip = File(dir, "malicious.zip")
        ZipOutputStream(FileOutputStream(zip)).use { out ->
            out.putNextEntry(java.util.zip.ZipEntry("vosk-model-small-pt-0.3/../../segredo"))
            out.write("vazamento".toByteArray())
            out.closeEntry()
        }
        val target = File(dir, VoskLayout.DIR_NAME)

        assertNull(VoskModelStore.extractFromZip(zip, target))
        assertFalse(target.exists())
        assertFalse(File(dir, "segredo").exists())
    }

    @Test
    fun alvoInvalidoESubstituidoPorExtracaoValida() {
        val dir = tempDir()
        val zip = File(dir, "model.zip")
        assetZip().copyTo(zip)
        val target = File(dir, VoskLayout.DIR_NAME)
        target.mkdirs()
        FileOutputStream(File(target, "final.mdl")).use { it.write(byteArrayOf(1)) }

        val result = VoskModelStore.extractFromZip(zip, target)

        assertNotNull(result)
        assertTrue(VoskLayout.isValidModelDir(target))
        assertEquals(
            VoskLayout.EXPECTED_UNCOMPRESSED_BYTES,
            target.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        )
    }

    @Test
    fun zipInexistenteRetornaNullSemCriarDestino() {
        val dir = tempDir()
        val target = File(dir, VoskLayout.DIR_NAME)
        assertNull(VoskModelStore.extractFromZip(File(dir, "missing.zip"), target))
        assertFalse(target.exists())
    }
}
