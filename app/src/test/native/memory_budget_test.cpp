// Teste host da logica de budget compartilhada com o JNI.
// Compilado e executado por scripts/test_memory_budget.ps1 (g++ -std=c++17).
#include "../../main/cpp/memory_budget.h"

#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <string>

static int g_failures = 0;
static int g_checks = 0;

static void check(bool condition, const std::string& label) {
    ++g_checks;
    if (!condition) {
        ++g_failures;
        std::fprintf(stderr, "FALHOU: %s\n", label.c_str());
    }
}

static bool near(double a, double b) { return std::fabs(a - b) < 1e-9; }

static uint64_t nextRandom(uint64_t& state) {
    state ^= state << 13;
    state ^= state >> 7;
    state ^= state << 17;
    return state;
}

int main() {
    // Vetores identicos aos de MemoryBudgetVectorTest.kt (4B Q2_0: 36 camadas,
    // 8 heads KV, embedding 2560 -> headDim 320).
    check(near(glia::kvBytesPerToken(36, 8, 2560, true), 196352.0), "kv q8_0 4B");
    check(near(glia::kvBytesPerToken(36, 8, 2560, false), 369152.0), "kv f16 4B");
    check(near(glia::kvBytesPerToken(0, 0, 0, true), 514.125), "kv degenerate");
    check(near(glia::kvBytesPerToken(1, 8, 4, true), 529.0), "kv headDim clamp");

    check(glia::maxContextByBudget(804257792LL, 36, 8, 2560, true) == 4096, "ctx 4096");
    check(glia::maxContextByBudget(201064448LL, 36, 8, 2560, true) == 1024, "ctx 1024");
    check(glia::maxContextByBudget(50266112LL, 36, 8, 2560, true) == 256, "ctx minimo exato");
    check(glia::maxContextByBudget(50266111LL, 36, 8, 2560, true) == 256, "ctx abaixo do minimo");
    check(glia::maxContextByBudget(0LL, 36, 8, 2560, true) == 256, "ctx budget zero");
    check(glia::maxContextByBudget(-1LL, 36, 8, 2560, true) == 256, "ctx budget negativo");
    check(glia::maxContextByBudget(369152LL * 512LL, 36, 8, 2560, false) == 512, "ctx f16 512");

    check(glia::fitsMinimum(50266112LL, 36, 8, 2560, true), "fits minimo exato");
    check(!glia::fitsMinimum(50266111LL, 36, 8, 2560, true), "nao fita minimo");
    check(!glia::fitsMinimum(0LL, 36, 8, 2560, true), "nao fita zero");
    check(!glia::fitsMinimum(-5LL, 36, 8, 2560, true), "nao fita negativo");

    // Invariantes propriedade-based com PRNG deterministically (100 mil casos).
    uint64_t state = 0x9E3779B97F4A7C15ULL;
    for (int i = 0; i < 100000; ++i) {
        const int layers = static_cast<int>(nextRandom(state) % 80);
        const int heads = static_cast<int>(nextRandom(state) % 16);
        const int embd = static_cast<int>(nextRandom(state) % 8192);
        const bool quantized = (nextRandom(state) & 1ULL) != 0;
        const int64_t budget = static_cast<int64_t>(nextRandom(state) % 2000000000000ULL);
        const double perToken = glia::kvBytesPerToken(layers, heads, embd, quantized);
        const int ctx = glia::maxContextByBudget(budget, layers, heads, embd, quantized);
        const bool fits = glia::fitsMinimum(budget, layers, heads, embd, quantized);

        check(perToken > 0.0, "perToken positivo");
        check(ctx >= glia::kMinContextTokens && ctx <= glia::kMaxContextTokens, "ctx intervalo");
        if (fits) {
            check(static_cast<double>(budget) / perToken >= glia::kMinContextTokens, "fits coerente");
        } else {
            check(static_cast<double>(budget) / perToken < glia::kMinContextTokens ||
                      budget <= 0,
                  "nao fita coerente");
        }
        const int nextCtx = glia::maxContextByBudget(budget + 1, layers, heads, embd, quantized);
        check(nextCtx >= ctx, "monotonico");
    }

    std::printf("budget nativo: %d verificacoes, %d falhas\n", g_checks, g_failures);
    return g_failures == 0 ? 0 : 1;
}
