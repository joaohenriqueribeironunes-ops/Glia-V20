#ifndef GLIA_MEMORY_BUDGET_H
#define GLIA_MEMORY_BUDGET_H

#include <algorithm>
#include <cmath>
#include <cstdint>

// Logica compartilhada com MemoryBudget.kt. Qualquer alteracao aqui deve
// atualizar os vetores de teste em MemoryBudgetVectorTest.kt e no teste nativo.
namespace glia {

constexpr int kMinContextTokens = 256;
constexpr int kMaxContextTokens = 4096;
constexpr double kQ80BytesPerElement = 1.0625;
constexpr double kF16BytesPerElement = 2.0;
constexpr double kExtraBytesPerToken = 512.0;

inline double kvBytesPerToken(int nLayer, int nHeadKv, int nEmbd, bool quantized = true) {
    const double layers = static_cast<double>(std::max(1, nLayer));
    const int heads = std::max(1, nHeadKv);
    const int headDim = std::max(1, nEmbd / heads);
    const double perElement = quantized ? kQ80BytesPerElement : kF16BytesPerElement;
    return 2.0 * layers * static_cast<double>(heads) * static_cast<double>(headDim) * perElement +
           kExtraBytesPerToken;
}

inline int maxContextByBudget(int64_t budgetBytes, int nLayer, int nHeadKv, int nEmbd,
                              bool quantized = true) {
    if (budgetBytes <= 0) return kMinContextTokens;
    const double perToken = kvBytesPerToken(nLayer, nHeadKv, nEmbd, quantized);
    const double fit = static_cast<double>(budgetBytes) / perToken;
    if (fit < kMinContextTokens) return kMinContextTokens;
    if (fit > kMaxContextTokens) return kMaxContextTokens;
    return static_cast<int>(fit);
}

inline bool fitsMinimum(int64_t budgetBytes, int nLayer, int nHeadKv, int nEmbd,
                        bool quantized = true) {
    if (budgetBytes <= 0) return false;
    return static_cast<double>(budgetBytes) >=
           kMinContextTokens * kvBytesPerToken(nLayer, nHeadKv, nEmbd, quantized);
}

}  // namespace glia

#endif  // GLIA_MEMORY_BUDGET_H
