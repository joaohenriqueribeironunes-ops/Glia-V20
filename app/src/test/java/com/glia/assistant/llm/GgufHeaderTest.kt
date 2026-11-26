package com.glia.assistant.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File

class GgufHeaderTest {

    private fun u32(out: ByteArrayOutputStream, value: Int) {
        repeat(4) { out.write((value ushr (8 * it)) and 0xFF) }
    }

    private fun u64(out: ByteArrayOutputStream, value: Long) {
        repeat(8) { out.write(((value ushr (8 * it)) and 0xFF).toInt()) }
    }

    private fun str(out: ByteArrayOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        u64(out, bytes.size.toLong())
        out.write(bytes)
    }

    private fun syntheticHeader(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("GGUF".toByteArray())
        u32(out, 3)
        u64(out, 1L) // tensor_count
        u64(out, 9L) // metadata_count
        str(out, "general.architecture"); u32(out, 8); str(out, "llama")
        str(out, "llama.context_length"); u32(out, 10); u64(out, 4096L)
        str(out, "llama.block_count"); u32(out, 4); u32(out, 36)
        str(out, "llama.embedding_length"); u32(out, 4); u32(out, 2560)
        str(out, "llama.attention.head_count_kv"); u32(out, 4); u32(out, 8)
        str(out, "general.file_type"); u32(out, 4); u32(out, 2)
        str(out, "general.temperature"); u32(out, 6); u32(out, java.lang.Float.floatToIntBits(0.7f))
        str(out, "tokenizer.ggml.tokens"); u32(out, 9); u64(out, 2L); u32(out, 8); str(out, "a"); str(out, "b")
        str(out, "general.quantized"); u32(out, 7); u32(out, 1)
        return out.toByteArray()
    }

    @Test
    fun analisaTodosOsTiposDeMetadado() {
        val header = GgufHeader.read(ByteArrayInputStream(syntheticHeader()))
        assertEquals(3, header.version)
        assertEquals(1L, header.tensorCount)
        assertEquals(9L, header.metadataCount)
        assertEquals("llama", header.architecture)
        assertEquals(4096L, header.contextLength)
        assertEquals(36L, header.blockCount)
        assertEquals(2560L, header.embeddingLength)
        assertEquals(8L, header.headCountKv)
        assertEquals(2L, header.fileType)
        assertEquals(0.7, (header.metadata["general.temperature"] as Double), 1e-6)
        assertEquals(listOf("a", "b"), header.metadata["tokenizer.ggml.tokens"])
        assertEquals(true, header.metadata["general.quantized"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun magicInvalidoEhRejeitado() {
        val bytes = syntheticHeader()
        bytes[1] = 'X'.code.toByte()
        GgufHeader.read(ByteArrayInputStream(bytes))
    }

    @Test(expected = IllegalArgumentException::class)
    fun versaoNaoSuportadaEhRejeitada() {
        val bytes = syntheticHeader()
        bytes[4] = 99
        GgufHeader.read(ByteArrayInputStream(bytes))
    }

    @Test(expected = EOFException::class)
    fun cabecalhoTruncadoEhRejeitado() {
        val bytes = syntheticHeader()
        GgufHeader.read(ByteArrayInputStream(bytes.copyOf(bytes.size - 3)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun tamanhoDeStringSuspeitoEhRejeitado() {
        val out = ByteArrayOutputStream()
        out.write("GGUF".toByteArray())
        u32(out, 3)
        u64(out, 0L)
        u64(out, 1L)
        str(out, "k")
        u32(out, 8)
        u64(out, 10L * 1024 * 1024)
        GgufHeader.read(ByteArrayInputStream(out.toByteArray()))
    }

    @Test
    fun modeloReal4BQuandoDisponivel() {
        val path = System.getProperty("glia.model.path")
        assumeTrue("modelo real nao informado (-Dglia.model.path=...)", !path.isNullOrBlank())
        val file = File(path)
        assumeTrue("arquivo de modelo ausente", file.isFile)

        assertTrue(ModelDiscovery.isValidGguf(file, ModelDiscovery.EXPECTED_MODEL_SIZE))
        val header = GgufHeader.read(file)
        assertTrue(header.version in 2..3)
        assertTrue(header.tensorCount > 0)
        assertNotNull(header.architecture)
        assertTrue(header.contextLength!! >= 2048L)
        assertTrue(header.blockCount!! > 0L)
        assertTrue(header.embeddingLength!! > 0L)
        assertTrue(header.headCountKv!! > 0L)
        assertEquals(
            "6e1ce909032e1afa7a88e68a3d628ecafff302bdf195befab308826c395e93b7".length,
            ModelStore.EXPECTED_SHA256.length
        )
    }
}
