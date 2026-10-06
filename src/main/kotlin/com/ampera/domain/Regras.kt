package com.ampera.domain

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

enum class Grandeza { TENSAO, CORRENTE, POTENCIA }

enum class TipoSensor { TENSAO, CORRENTE }

enum class OrigemMedicao { MQTT, MANUAL }

class RecursoNaoEncontradoException(mensagem: String) : RuntimeException(mensagem)

class AcessoNegadoException(mensagem: String) : RuntimeException(mensagem)

class RegraDeNegocioException(mensagem: String) : RuntimeException(mensagem)

class CalculadoraDeCusto {
    fun calcular(energiaKwh: Double, valorKwh: BigDecimal): BigDecimal =
        valorKwh.multiply(BigDecimal.valueOf(energiaKwh)).setScale(4, RoundingMode.HALF_UP)
}

class AvaliadorDeAlerta {
    fun disparou(
        grandeza: Grandeza,
        limiar: Double,
        tensao: Double,
        corrente: Double,
        potencia: Double,
    ): Boolean {
        val valor = when (grandeza) {
            Grandeza.TENSAO -> tensao
            Grandeza.CORRENTE -> corrente
            Grandeza.POTENCIA -> potencia
        }
        return valor > limiar
    }
}

class LinhaDeBaseEnergia {
    fun reiniciou(anterior: Double?, atual: Double): Boolean = anterior != null && atual < anterior
}

class PrevisorDeConsumo {
    fun preverProximo(consumosDiarios: List<Double>): Double {
        if (consumosDiarios.isEmpty()) return 0.0
        if (consumosDiarios.size == 1) return consumosDiarios.first().coerceAtLeast(0.0)
        val xs = consumosDiarios.indices.map { it.toDouble() }
        val mediaX = xs.average()
        val mediaY = consumosDiarios.average()
        val numerador = xs.zip(consumosDiarios).sumOf { (x, y) -> (x - mediaX) * (y - mediaY) }
        val denominador = xs.sumOf { (it - mediaX).pow(2) }
        val inclinacao = if (denominador == 0.0) 0.0 else numerador / denominador
        val intercepto = mediaY - inclinacao * mediaX
        return (intercepto + inclinacao * consumosDiarios.size).coerceAtLeast(0.0)
    }
}

class AcessoPorDono {
    fun exigir(donoId: Long, atualId: Long) {
        if (donoId != atualId) {
            throw AcessoNegadoException("Recurso de outro usuário")
        }
    }
}

class ConsumoDoPeriodo {
    fun diario(leituras: List<LeituraEnergia>): List<Double> {
        return leituras
            .groupBy { it.dia }
            .toSortedMap()
            .values
            .map { dia -> deltas(dia.sortedBy { it.instanteMillis }.map { it.energiaKwh }) }
    }

    fun deltas(energias: List<Double>): Double {
        var total = 0.0
        var anterior: Double? = null
        energias.forEach { atual ->
            if (anterior != null) {
                total += if (atual < anterior!!) atual else atual - anterior!!
            }
            anterior = atual
        }
        return total
    }
}

data class LeituraDoMedidor(
    val codigoMqtt: String,
    val tensao: Double,
    val corrente: Double,
    val potencia: Double,
    val energiaKwh: Double,
)

data class LeituraEnergia(
    val dia: String,
    val instanteMillis: Long,
    val energiaKwh: Double,
)
