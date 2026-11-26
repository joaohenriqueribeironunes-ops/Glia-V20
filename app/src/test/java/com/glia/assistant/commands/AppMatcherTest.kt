package com.glia.assistant.commands

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMatcherTest {
    private val apps = listOf(
        AppEntry("WhatsApp", "com.whatsapp"),
        AppEntry("WhatsApp Business", "com.whatsapp.w4b"),
        AppEntry("Câmera", "com.android.camera"),
        AppEntry("Configurações", "com.android.settings"),
        AppEntry("YouTube", "com.google.android.youtube"),
        AppEntry("Galeria", "com.android.gallery")
    )

    @Test
    fun encontraAplicativoComNomeExatoIgnorandoAcentos() {
        assertEquals("com.whatsapp", AppMatcher.bestMatch("whatsapp", apps)?.packageName)
        assertEquals("com.android.camera", AppMatcher.bestMatch("camera", apps)?.packageName)
        assertEquals("com.android.settings", AppMatcher.bestMatch("configuracoes", apps)?.packageName)
    }

    @Test
    fun prefereNomeMaisCurtoQuandoAmbosComecamIgual() {
        val match = AppMatcher.bestMatch("whatsapp", apps)
        assertEquals("WhatsApp", match?.label)
    }

    @Test
    fun encontraPorPrefixoEPorTrecho() {
        assertEquals("com.google.android.youtube", AppMatcher.bestMatch("you", apps)?.packageName)
        assertEquals("com.android.gallery", AppMatcher.bestMatch("galeria de fotos", apps)?.packageName)
    }

    @Test
    fun retornaNullQuandoNaoHaCorrespondencia() {
        assertNull(AppMatcher.bestMatch("banco", apps))
        assertNull(AppMatcher.bestMatch("", apps))
    }

    @Test
    fun pontuacaoRespeitaHierarquia() {
        assertTrue(AppMatcher.score("whatsapp", "WhatsApp") > AppMatcher.score("whats", "WhatsApp"))
        assertTrue(AppMatcher.score("whats", "WhatsApp") > AppMatcher.score("app", "WhatsApp"))
        assertEquals(0, AppMatcher.score("banco", "WhatsApp"))
    }

    @Test
    fun nomeComPontuacaoNormalizaParaEspaco() {
        assertEquals(100, AppMatcher.score("whatsapp business", "WhatsApp Business"))
    }
}
