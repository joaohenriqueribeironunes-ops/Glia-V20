package com.glia.assistant.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class OfflineVoiceTest {

    private fun asset(voice: OfflineVoice): File =
        File("app/src/main/assets/${voice.assetName}")

    @Test
    fun `ids conhecidos selecionam Dii e Cadu`() {
        assertEquals(OfflineVoices.DII, OfflineVoices.fromId("dii"))
        assertEquals(OfflineVoices.CADU, OfflineVoices.fromId("cadu"))
        assertEquals(OfflineVoices.DII, OfflineVoices.fromId("inexistente"))
        assertNotNull(OfflineVoices.fromId(null))
    }

    @Test
    fun `asset Cadu tem hash tamanho e estrutura esperados`() {
        val zipFile = asset(OfflineVoices.CADU)
        assertTrue(zipFile.isFile)
        assertEquals(OfflineVoices.CADU_ZIP_BYTES, zipFile.length())
        assertEquals(OfflineVoices.CADU.zipSha256, OfflineVoices.sha256(zipFile))

        ZipFile(zipFile).use { zip ->
            assertNullMessage(OfflineVoices.validateZip(OfflineVoices.CADU, zip))
            assertEquals(OfflineVoices.CADU.entryCount, zip.size())
            assertEquals(
                OfflineVoices.CADU.uncompressedBytes,
                OfflineVoices.uncompressedBytes(zip)
            )
            val model = zip.getEntry("${OfflineVoices.CADU.dirName}/${OfflineVoices.CADU.modelName}")
                ?: throw AssertionError("entrada do modelo Cadu ausente")
            assertEquals(18_343_859L, model.size)
        }
    }

    @Test
    fun `asset Dii continua valido pela fachada`() {
        val zipFile = asset(OfflineVoices.DII)
        assertEquals(58_800_891L, zipFile.length())
        assertEquals(OfflineVoices.DII.zipSha256, OfflineVoices.sha256(zipFile))
        ZipFile(zipFile).use { zip ->
            assertNullMessage(OfflineVoices.validateZip(OfflineVoices.DII, zip))
        }
    }

    @Test
    fun `caminhos do Cadu rejeitam traversal e prefixo errado`() {
        val voice = OfflineVoices.CADU
        val prefix = "${voice.dirName}/"
        assertEquals("tokens.txt", OfflineVoices.safeRelative(voice, "${prefix}tokens.txt"))
        assertFalse(OfflineVoices.safeRelative(voice, "glia-neural-voice/tokens.txt")?.startsWith(prefix) == true)
        assertEquals(null, OfflineVoices.safeRelative(voice, "${prefix}../segredo"))
        assertEquals(null, OfflineVoices.safeRelative(voice, "/etc/passwd"))
        assertEquals(null, OfflineVoices.safeRelative(voice, "C:/windows/system32"))
    }

    @Test
    fun `arquivos obrigatorios incluem modelo Cadu e espeak`() {
        val required = OfflineVoices.CADU.requiredFiles
        assertTrue(required.contains(OfflineVoices.CADU.modelName))
        assertTrue(required.contains(OfflineVoices.CADU.tokensName))
        assertTrue(required.contains("${OfflineVoices.CADU.espeakDir}/lang/roa/pt-BR"))
        assertEquals(11, required.size)
    }

    private fun assertNullMessage(value: String?) {
        if (value != null) throw AssertionError(value)
    }
}
