package com.ampera.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class RegrasTest {
    @Test
    fun `custo multiplica energia pela tarifa`() {
        val custo = CalculadoraDeCusto().calcular(0.0123, BigDecimal("1.0000"))
        assertEquals(BigDecimal("0.0123"), custo)
    }

    @Test
    fun `alerta dispara quando a grandeza passa do limiar`() {
        val avaliador = AvaliadorDeAlerta()
        assertTrue(avaliador.disparou(Grandeza.POTENCIA, 100.0, 127.0, 0.8, 107.1))
        assertFalse(avaliador.disparou(Grandeza.TENSAO, 200.0, 127.0, 0.8, 107.1))
    }

    @Test
    fun `contador menor que o anterior abre nova linha de base`() {
        val linha = LinhaDeBaseEnergia()
        assertTrue(linha.reiniciou(1.5, 0.01))
        assertFalse(linha.reiniciou(1.5, 1.6))
        assertFalse(linha.reiniciou(null, 0.01))
    }

    @Test
    fun `regressao linear projeta o proximo dia`() {
        val previsao = PrevisorDeConsumo().preverProximo(listOf(1.0, 2.0, 3.0))
        assertEquals(4.0, previsao, 0.0001)
    }

    @Test
    fun `consumo do periodo soma apenas os deltas e respeita reinicio`() {
        val total = ConsumoDoPeriodo().deltas(listOf(1.0, 1.4, 0.2, 0.5))
        assertEquals(0.9, total, 0.0001)
    }

    @Test
    fun `acesso nega recurso de outro usuario`() {
        assertThrows(AcessoNegadoException::class.java) {
            AcessoPorDono().exigir(donoId = 2, atualId = 1)
        }
    }
}
