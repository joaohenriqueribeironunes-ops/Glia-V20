package com.glia.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Vetores compartilhados com memory_budget_test.cpp e provas propriedade-based. */
class MemoryBudgetVectorTest {

    @Test
    fun vetoresDeBytesPorTokenSaoIdenticosAoNativo() {
        assertEquals(196_352.0, MemoryBudget.kvBytesPerToken(36, 8, 2560, true), 1e-9)
        assertEquals(369_152.0, MemoryBudget.kvBytesPerToken(36, 8, 2560, false), 1e-9)
        assertEquals(514.125, MemoryBudget.kvBytesPerToken(0, 0, 0, true), 1e-9)
        assertEquals(529.0, MemoryBudget.kvBytesPerToken(1, 8, 4, true), 1e-9)
    }

    @Test
    fun vetoresDeContextoSaoIdenticosAoNativo() {
        assertEquals(4096, MemoryBudget.maxContextByBudget(804_257_792, 36, 8, 2560, true))
        assertEquals(1024, MemoryBudget.maxContextByBudget(201_064_448, 36, 8, 2560, true))
        assertEquals(256, MemoryBudget.maxContextByBudget(50_266_112, 36, 8, 2560, true))
        assertEquals(256, MemoryBudget.maxContextByBudget(50_266_111, 36, 8, 2560, true))
        assertEquals(256, MemoryBudget.maxContextByBudget(0, 36, 8, 2560, true))
        assertEquals(256, MemoryBudget.maxContextByBudget(-1, 36, 8, 2560, true))
        assertEquals(512, MemoryBudget.maxContextByBudget(189_005_824, 36, 8, 2560, false))
    }

    @Test
    fun admissionDoContextoMinimo() {
        assertTrue(MemoryBudget.fitsMinimum(50_266_112, 36, 8, 2560, true))
        assertFalse(MemoryBudget.fitsMinimum(50_266_111, 36, 8, 2560, true))
        assertFalse(MemoryBudget.fitsMinimum(0, 36, 8, 2560, true))
        assertFalse(MemoryBudget.fitsMinimum(-5, 36, 8, 2560, true))
    }

    @Test
    fun planoRespeitaFronteirasDoOrcamento() {
        val base = MemoryBudget.SYSTEM_HEADROOM_BYTES +
            MemoryBudget.RUNTIME_RESERVE_BYTES + 1_212_103_520L
        val minimum = 256L * MemoryBudget.ESTIMATED_BYTES_PER_TOKEN

        val exato = MemoryBudget.planBytes(base + minimum, 1_212_103_520L, 1024)
        assertTrue(exato.ok)
        assertEquals(minimum, exato.budgetBytes)
        assertEquals(256, exato.contextTokens)

        val insuficiente = MemoryBudget.planBytes(base + minimum - 1, 1_212_103_520L, 1024)
        assertFalse(insuficiente.ok)
        assertEquals(256, insuficiente.contextTokens)
        assertTrue(insuficiente.message.contains("insuficiente"))

        val abundante = MemoryBudget.planBytes(
            base + 64L * 1024 * 1024 * 1024, 1_212_103_520L, 9999
        )
        assertTrue(abundante.ok)
        assertEquals(MemoryBudget.MAX_CONTEXT_TOKENS, abundante.contextTokens)
    }

    @Test
    fun planoComMemoriaNaoMedidaNaoBloqueia() {
        val plan = MemoryBudget.planBytes(0, 1_212_103_520L, 777)
        assertTrue(plan.ok)
        assertEquals(777, plan.contextTokens)
        assertTrue(plan.message.contains("medicao"))
    }

    @Test
    fun relacionamentoEntreOrcamentoEPlano() {
        val available = 5_500_000_000L
        val model = 1_212_103_520L
        assertEquals(
            MemoryBudget.budgetBytes(available, model),
            MemoryBudget.planBytes(available, model, 1024).budgetBytes
        )
        assertEquals(
            available - MemoryBudget.SYSTEM_HEADROOM_BYTES - MemoryBudget.RUNTIME_RESERVE_BYTES,
            MemoryBudget.usableBytes(available)
        )
    }

    @Test
    fun contextoCresceComOrcamento() {
        var previous = 0
        for (budget in 0L..2_000_000L step 10_000L) {
            val ctx = MemoryBudget.maxContextByBudget(budget, 36, 8, 2560, true)
            assertTrue("ctx deve ser monotonico em $budget", ctx >= previous)
            previous = ctx
        }
    }

    @Test
    fun propriedadesComCemMilCasosDeterministicos() {
        val random = Random(0x5DEECE66DL)
        repeat(100_000) {
            val layers = random.nextInt(81)
            val heads = random.nextInt(17)
            val embd = random.nextInt(8193)
            val quantized = random.nextBoolean()
            val budget = random.nextLong().let { if (it < 0) -(it + 1) else it } % 2_000_000_000_000L

            val perToken = MemoryBudget.kvBytesPerToken(layers, heads, embd, quantized)
            val ctx = MemoryBudget.maxContextByBudget(budget, layers, heads, embd, quantized)
            val fits = MemoryBudget.fitsMinimum(budget, layers, heads, embd, quantized)

            assertTrue("perToken deve ser positivo", perToken > 0.0)
            assertTrue(ctx >= 256 && ctx <= 4096)
            if (fits) {
                assertTrue(budget.toDouble() / perToken >= 256.0)
            } else {
                assertTrue(budget.toDouble() / perToken < 256.0 || budget <= 0L)
            }
        }
    }
}
