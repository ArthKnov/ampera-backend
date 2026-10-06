package com.ampera.api

import com.ampera.domain.Grandeza
import com.ampera.domain.OrigemMedicao
import com.ampera.domain.TipoSensor
import com.ampera.infrastructure.persistence.Alerta
import com.ampera.infrastructure.persistence.Comodo
import com.ampera.infrastructure.persistence.Dispositivo
import com.ampera.infrastructure.persistence.Medicao
import com.ampera.infrastructure.persistence.MetaConsumo
import com.ampera.infrastructure.persistence.Relatorio
import com.ampera.infrastructure.persistence.Residencia
import com.ampera.infrastructure.persistence.Sensor
import com.ampera.infrastructure.persistence.Tarifa
import com.ampera.infrastructure.persistence.Usuario
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class ErroResponse(val mensagem: String?)

data class LoginRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val senha: String,
)

data class LoginResponse(val token: String, val usuario: UsuarioResponse)

data class UsuarioRequest(
    @field:NotBlank val nome: String,
    @field:Email @field:NotBlank val email: String,
    val senha: String? = null,
)

data class UsuarioResponse(val id: Long, val nome: String, val email: String)

data class ResidenciaRequest(
    @field:NotBlank val nome: String,
    @field:NotBlank val endereco: String,
)

data class ResidenciaResponse(val id: Long, val usuarioId: Long, val nome: String, val endereco: String)

data class ComodoRequest(
    @field:NotNull val residenciaId: Long,
    @field:NotBlank val nome: String,
)

data class ComodoResponse(val id: Long, val residenciaId: Long, val nome: String)

data class DispositivoRequest(
    @field:NotNull val comodoId: Long,
    @field:NotBlank val nome: String,
    @field:NotBlank val codigoMqtt: String,
)

data class DispositivoResponse(val id: Long, val comodoId: Long, val nome: String, val codigoMqtt: String)

data class SensorRequest(
    @field:NotNull val dispositivoId: Long,
    @field:NotNull val tipo: TipoSensor,
    @field:NotBlank val unidade: String,
)

data class SensorResponse(val id: Long, val dispositivoId: Long, val tipo: TipoSensor, val unidade: String)

data class MedicaoRequest(
    @field:NotNull val dispositivoId: Long,
    val tensao: Double,
    val corrente: Double,
    val potencia: Double,
    val energiaKwh: Double,
)

data class MedicaoResponse(
    val id: Long,
    val dispositivoId: Long,
    val tensao: Double,
    val corrente: Double,
    val potencia: Double,
    val energiaKwh: Double,
    val custo: BigDecimal,
    val instante: Instant,
    val origem: OrigemMedicao,
)

data class TarifaRequest(
    @field:NotNull val residenciaId: Long,
    @field:NotNull val valorKwh: BigDecimal,
    @field:NotNull val vigenciaInicio: LocalDate,
    val vigenciaFim: LocalDate? = null,
)

data class TarifaResponse(
    val id: Long,
    val residenciaId: Long,
    val valorKwh: BigDecimal,
    val vigenciaInicio: LocalDate,
    val vigenciaFim: LocalDate?,
)

data class AlertaRequest(
    @field:NotNull val dispositivoId: Long,
    @field:NotNull val grandeza: Grandeza,
    val limiar: Double,
    @field:NotBlank val mensagem: String,
    val ativo: Boolean = true,
)

data class AlertaResponse(
    val id: Long,
    val dispositivoId: Long,
    val grandeza: Grandeza,
    val limiar: Double,
    val mensagem: String,
    val ativo: Boolean,
    val disparado: Boolean,
)

data class MetaConsumoRequest(
    @field:NotNull val residenciaId: Long,
    val limiteKwh: Double,
    @field:NotNull val inicio: LocalDate,
    @field:NotNull val fim: LocalDate,
)

data class MetaConsumoResponse(
    val id: Long,
    val residenciaId: Long,
    val limiteKwh: Double,
    val inicio: LocalDate,
    val fim: LocalDate,
    val consumoKwh: Double,
)

data class RelatorioRequest(
    @field:NotNull val residenciaId: Long,
    @field:NotNull val inicio: LocalDate,
    @field:NotNull val fim: LocalDate,
    val consumoKwh: Double = 0.0,
    val custoTotal: BigDecimal = BigDecimal.ZERO,
    val previsaoKwh: Double = 0.0,
)

data class GerarRelatorioRequest(
    @field:NotNull val residenciaId: Long,
    @field:NotNull val inicio: LocalDate,
    @field:NotNull val fim: LocalDate,
)

data class RelatorioResponse(
    val id: Long,
    val residenciaId: Long,
    val inicio: LocalDate,
    val fim: LocalDate,
    val consumoKwh: Double,
    val custoTotal: BigDecimal,
    val previsaoKwh: Double,
    val criadoEm: Instant,
)

fun Usuario.paraResposta() = UsuarioResponse(id, nome, email)
fun Residencia.paraResposta() = ResidenciaResponse(id, usuario.id, nome, endereco)
fun Comodo.paraResposta() = ComodoResponse(id, residencia.id, nome)
fun Dispositivo.paraResposta() = DispositivoResponse(id, comodo.id, nome, codigoMqtt)
fun Sensor.paraResposta() = SensorResponse(id, dispositivo.id, tipo, unidade)
fun Medicao.paraResposta() = MedicaoResponse(id, dispositivo.id, tensao, corrente, potencia, energiaKwh, custo, instante, origem)
fun Tarifa.paraResposta() = TarifaResponse(id, residencia.id, valorKwh, vigenciaInicio, vigenciaFim)
fun Alerta.paraResposta() = AlertaResponse(id, dispositivo.id, grandeza, limiar, mensagem, ativo, disparado)
fun MetaConsumo.paraResposta(consumoKwh: Double) = MetaConsumoResponse(id, residencia.id, limiteKwh, inicio, fim, consumoKwh)
fun Relatorio.paraResposta() = RelatorioResponse(id, residencia.id, inicio, fim, consumoKwh, custoTotal, previsaoKwh, criadoEm)
