package com.glia.assistant.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

class ModelDiscoveryTest {

    private fun tempDir(): File = Files.createTempDirectory("glia-model-test-").toFile()

    private fun writeGguf(file: File, size: Int = 4096): File {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            out.write(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()))
            out.write(byteArrayOf(3, 0, 0, 0))
            out.write(ByteArray(size - 8))
        }
        assertEquals(size.toLong(), file.length())
        return file
    }

    private fun dirs(
        privateDir: File,
        savedPath: String? = null,
        appDownload: File? = null,
        publicDirs: List<File> = emptyList()
    ) = ModelDirectories(savedPath, privateDir, appDownload, publicDirs)

    @Test
    fun magicEhValidadoByte_a_Byte() {
        assertTrue(ModelDiscovery.isGgufMagic("GGUF".toByteArray()))
        assertTrue(ModelDiscovery.isGgufMagic("XXGGUFYY".toByteArray(), 2))
        assertFalse(ModelDiscovery.isGgufMagic("GGUf".toByteArray()))
        assertFalse(ModelDiscovery.isGgufMagic("GGU".toByteArray()))
        assertFalse(ModelDiscovery.isGgufMagic("GGUF".toByteArray(), 1))
    }

    @Test
    fun arquivoValidoExigeMagicETamanho() {
        val dir = tempDir()
        val valid = writeGguf(File(dir, "model.gguf"))
        assertTrue(ModelDiscovery.isValidGguf(valid, valid.length()))
        assertFalse(ModelDiscovery.isValidGguf(valid, valid.length() + 1))

        val noMagic = File(dir, "bad.gguf")
        FileOutputStream(noMagic).use { it.write(ByteArray(4096)) }
        assertFalse(ModelDiscovery.isValidGguf(noMagic))

        val tiny = File(dir, "tiny.gguf")
        FileOutputStream(tiny).use { it.write("GGUF".toByteArray()) }
        assertFalse(ModelDiscovery.isValidGguf(tiny))

        assertFalse(ModelDiscovery.isValidGguf(File(dir, "missing.gguf")))
        assertFalse(ModelDiscovery.isValidGguf(dir))
    }

    @Test
    fun modeloEncontradoNoCaminhoSalvoECopiadoParaPrivado() {
        val privateDir = tempDir()
        val external = tempDir()
        val source = writeGguf(File(external, "bonsai.gguf"), 8192)
        val result = ModelDiscovery.discover(
            dirs(privateDir, savedPath = source.absolutePath),
            expectedSize = source.length()
        )

        assertNotNull(result.file)
        assertEquals(File(privateDir, "user-model.gguf").absolutePath, result.file!!.absolutePath)
        assertEquals(8192L, result.file.length())
        assertTrue(result.copiedToPrivate)
        assertNull(result.note)
        assertFalse(File(privateDir, "user-model.gguf.part").exists())
    }

    @Test
    fun modeloPublicoDoDownloadTemPrecedenciaSobrePdf() {
        val privateDir = tempDir()
        val publicDir = tempDir()
        val source = writeGguf(File(publicDir, "Ternary-Bonsai-2-4B-Q2_0.gguf"), 6000)
        val result = ModelDiscovery.discover(
            dirs(privateDir, publicDirs = listOf(publicDir)),
            expectedSize = source.length()
        )

        assertNotNull(result.file)
        assertEquals("user-model.gguf", result.file!!.name)
        assertTrue(result.copiedToPrivate)
        assertEquals(source.length(), result.file.length())
    }

    @Test
    fun outroGgufEUsadoComoFallbackComAviso() {
        val privateDir = tempDir()
        val publicDir = tempDir()
        val source = writeGguf(File(publicDir, "Ternary-Bonsai-2-4B-Q2_0.gguf"), 7000)
        val result = ModelDiscovery.discover(
            dirs(privateDir, publicDirs = listOf(publicDir)),
            expectedSize = 1_212_103_520L
        )

        assertNotNull(result.file)
        assertEquals("user-model.gguf", result.file!!.name)
        assertTrue(result.copiedToPrivate)
        assertNotNull(result.note)
        assertTrue(result.note!!.contains("diferente do 4B"))
    }

    @Test
    fun caminhoSalvoTemPrecedenciaSobrePrivadoEPublico() {
        val privateDir = tempDir()
        val publicDir = tempDir()
        val saved = writeGguf(File(tempDir(), "saved.gguf"), 5000)
        writeGguf(File(privateDir, "user-model.gguf"), 6000)
        writeGguf(File(publicDir, "Ternary-Bonsai-2-4B-Q2_0.gguf"), 7000)

        val result = ModelDiscovery.discover(
            dirs(privateDir, savedPath = saved.absolutePath, publicDirs = listOf(publicDir)),
            expectedSize = 5000L
        )

        // O caminho salvo vem primeiro; ao casar com o tamanho esperado, e copiado
        // para o privado, sobrescrevendo o arquivo de outro tamanho.
        assertEquals(File(privateDir, "user-model.gguf").absolutePath, result.file!!.absolutePath)
        assertEquals(5000L, result.file.length())
        assertTrue(result.copiedToPrivate)
        assertNull(result.note)
    }

    @Test
    fun arquivoPrivadoInvalidoNaoVazaParaOResultado() {
        val privateDir = tempDir()
        val invalid = File(privateDir, "user-model.gguf")
        FileOutputStream(invalid).use { it.write(ByteArray(4096)) }
        val publicDir = tempDir()
        val source = writeGguf(File(publicDir, "Ternary-Bonsai-2-4B-Q2_0.gguf"), 9000)

        val result = ModelDiscovery.discover(
            dirs(privateDir, publicDirs = listOf(publicDir)),
            expectedSize = source.length()
        )

        assertEquals(source.length(), result.file!!.length())
        assertTrue(ModelDiscovery.isValidGguf(result.file!!, source.length()))
    }

    @Test
    fun parteCorrompidaESobrescritaSemPerderOModeloValido() {
        val privateDir = tempDir()
        val publicDir = tempDir()
        val source = writeGguf(File(publicDir, "Ternary-Bonsai-2-4B-Q2_0.gguf"), 3000)
        FileOutputStream(File(privateDir, "user-model.gguf.part")).use { it.write(ByteArray(16)) }

        val result = ModelDiscovery.discover(
            dirs(privateDir, publicDirs = listOf(publicDir)),
            expectedSize = source.length()
        )

        assertEquals(source.length(), result.file!!.length())
        assertFalse(File(privateDir, "user-model.gguf.part").exists())
    }

    @Test
    fun semCandidatosRetornaMensagemDeImportacao() {
        val result = ModelDiscovery.discover(dirs(tempDir()), expectedSize = 1_212_103_520L)
        assertNull(result.file)
        assertNotNull(result.note)
        assertTrue(result.note!!.contains("Ternary-Bonsai-2-4B-Q2_0.gguf"))
    }

    @Test
    fun candidatosDuplicadosSaoEliminados() {
        val privateDir = tempDir()
        val model = writeGguf(File(privateDir, "user-model.gguf"), 2000)
        val candidates = ModelDiscovery.candidateFiles(
            dirs(privateDir, savedPath = model.absolutePath)
        )
        assertEquals(2, candidates.size)
        assertEquals(model.absolutePath, candidates.first().absolutePath)
        assertEquals(
            File(privateDir, ModelDiscovery.RECOMMENDED_FILE_NAME).absolutePath,
            candidates[1].absolutePath
        )
    }

    @Test
    fun ordemDosCandidatosEPrevisivel() {
        val privateDir = tempDir()
        val appDownload = tempDir()
        val candidates = ModelDiscovery.candidateFiles(
            dirs(
                privateDir,
                savedPath = "C:/saved/model.gguf",
                appDownload = appDownload,
                publicDirs = listOf(File("/sdcard/Download"), File("/sdcard/Documents"))
            )
        )
        assertEquals(7, candidates.size)
        assertEquals("model.gguf", candidates[0].name)
        assertEquals("user-model.gguf", candidates[1].name)
        assertEquals("Ternary-Bonsai-2-4B-Q2_0.gguf", candidates[2].name)
        assertEquals("Ternary-Bonsai-2-4B-Q2_0.gguf", candidates[3].name)
        assertEquals("user-model.gguf", candidates[4].name)
        assertEquals("Ternary-Bonsai-2-4B-Q2_0.gguf", candidates[5].name)
        assertEquals("Ternary-Bonsai-2-4B-Q2_0.gguf", candidates[6].name)
    }
}
