package com.ampera.application

import com.ampera.api.AlertaRequest
import com.ampera.api.AlertaResponse
import com.ampera.api.GerarRelatorioRequest
import com.ampera.api.MedicaoRequest
import com.ampera.api.MedicaoResponse
import com.ampera.api.MetaConsumoRequest
import com.ampera.api.MetaConsumoResponse
import com.ampera.api.RelatorioRequest
import com.ampera.api.RelatorioResponse
import com.ampera.api.SensorRequest
import com.ampera.api.SensorResponse
import com.ampera.api.TarifaRequest
import com.ampera.api.TarifaResponse
import com.ampera.api.paraResposta
import com.ampera.domain.AvaliadorDeAlerta
import com.ampera.domain.CalculadoraDeCusto
import com.ampera.domain.ConsumoDoPeriodo
import com.ampera.domain.LeituraDoMedidor
import com.ampera.domain.LeituraEnergia
import com.ampera.domain.LinhaDeBaseEnergia
import com.ampera.domain.OrigemMedicao
import com.ampera.domain.PrevisorDeConsumo
import com.ampera.domain.RecursoNaoEncontradoException
import com.ampera.domain.RegraDeNegocioException
import com.ampera.infrastructure.persistence.Alerta
import com.ampera.infrastructure.persistence.AlertaRepository
import com.ampera.infrastructure.persistence.ComodoRepository
import com.ampera.infrastructure.persistence.Dispositivo
import com.ampera.infrastructure.persistence.DispositivoRepository
import com.ampera.infrastructure.persistence.Medicao
import com.ampera.infrastructure.persistence.MedicaoRepository
import com.ampera.infrastructure.persistence.MetaConsumo
import com.ampera.infrastructure.persistence.MetaConsumoRepository
import com.ampera.infrastructure.persistence.Relatorio
import com.ampera.infrastructure.persistence.RelatorioRepository
import com.ampera.infrastructure.persistence.Sensor
import com.ampera.infrastructure.persistence.SensorRepository
import com.ampera.infrastructure.persistence.Tarifa
import com.ampera.infrastructure.persistence.TarifaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Service
class SensorService(
    private val sensores: SensorRepository,
    private val donos: Donos,
    private val sessao: SessaoAtual,
    private val transacao: Transacao,
) {
    suspend fun listar(dispositivoId: Long?): List<SensorResponse> = io {
        val usuarioId = sessao.id()
        transacao.ler {
            val lista = if (dispositivoId == null) {
                sensores.findByDispositivoComodoResidenciaUsuarioId(usuarioId)
            } else {
                donos.dispositivo(dispositivoId, usuarioId)
                sensores.findByDispositivoIdAndDispositivoComodoResidenciaUsuarioId(dispositivoId, usuarioId)
            }
            lista.map { it.paraResposta() }
        }
    }

    suspend fun buscar(id: Long): SensorResponse = io {
        val usuarioId = sessao.id()
        transacao.ler { carregar(id, usuarioId).paraResposta() }
    }

    suspend fun criar(pedido: SensorRequest): SensorResponse = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            val dispositivo = donos.dispositivo(pedido.dispositivoId, usuarioId)
            sensores.save(Sensor(dispositivo = dispositivo, tipo = pedido.tipo, unidade = pedido.unidade.trim())).paraResposta()
        }
    }

    suspend fun atualizar(id: Long, pedido: SensorRequest): SensorResponse = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            val sensor = carregar(id, usuarioId)
            sensor.dispositivo = donos.dispositivo(pedido.dispositivoId, usuarioId)
            sensor.tipo = pedido.tipo
            sensor.unidade = pedido.unidade.trim()
            sensor.paraResposta()
        }
    }

    suspend fun excluir(id: Long) = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            carregar(id, usuarioId)
            sensores.deleteById(id)
        }
    }

    private fun carregar(id: Long, usuarioId: Long): Sensor {
        val sensor = sensores.findById(id).orElseThrow { RecursoNaoEncontradoException("Sensor não encontrado") }
        donos.dispositivo(sensor.dispositivo.id, usuarioId)
        return sensor
    }
}

@Service
class MedicaoService(
    private val medicoes: MedicaoRepository,
    private val dispositivos: DispositivoRepository,
    private val comodos: ComodoRepository,
    private val tarifas: TarifaRepository,
    private val alertas: AlertaRepository,
    private val donos: Donos,
    private val sessao: SessaoAtual,
    private val transacao: Transacao,
    private val calculadora: CalculadoraDeCusto = CalculadoraDeCusto(),
    private val avaliador: AvaliadorDeAlerta = AvaliadorDeAlerta(),
    private val linhaDeBase: LinhaDeBaseEnergia = LinhaDeBaseEnergia(),
) {
    suspend fun recentes(dispositivoId: Long?, limite: Int): Flow<MedicaoResponse> {
        val usuarioId = sessao.id()
        val itens = io { transacao.ler { buscar(usuarioId, dispositivoId, limite) } }
        return flow { itens.forEach { emit(it) } }
    }

    suspend fun buscar(id: Long): MedicaoResponse = io {
        val usuarioId = sessao.id()
        transacao.ler { carregar(id, usuarioId).paraResposta() }
    }

    suspend fun criar(pedido: MedicaoRequest): MedicaoResponse = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            val dispositivo = donos.dispositivo(pedido.dispositivoId, usuarioId)
            gravar(Medicao(instante = Instant.now()), dispositivo, pedido.leitura(dispositivo), OrigemMedicao.MANUAL).paraResposta()
        }
    }

    suspend fun atualizar(id: Long, pedido: MedicaoRequest): MedicaoResponse = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            val medicao = carregar(id, usuarioId)
            val dispositivo = donos.dispositivo(pedido.dispositivoId, usuarioId)
            gravar(medicao, dispositivo, pedido.leitura(dispositivo), OrigemMedicao.MANUAL).paraResposta()
        }
    }

    suspend fun registrarDoMedidor(leitura: LeituraDoMedidor) = io {
        transacao.escrever {
            val dispositivo = dispositivos.findByCodigoMqtt(leitura.codigoMqtt) ?: provisionar(leitura.codigoMqtt)
            if (dispositivo == null) {
                log.warn("Medição de {} ignorada: nenhum cômodo para provisionar o dispositivo", leitura.codigoMqtt)
            } else {
                gravar(Medicao(instante = Instant.now()), dispositivo, leitura, OrigemMedicao.MQTT)
            }
        }
    }

    suspend fun excluir(id: Long) = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            carregar(id, usuarioId)
            medicoes.deleteById(id)
        }
    }

    private fun buscar(usuarioId: Long, dispositivoId: Long?, limite: Int): List<MedicaoResponse> {
        val pagina = PageRequest.of(0, limite.coerceIn(1, 200))
        val lista = if (dispositivoId == null) {
            medicoes.findByDispositivoComodoResidenciaUsuarioIdOrderByInstanteDesc(usuarioId, pagina)
        } else {
            donos.dispositivo(dispositivoId, usuarioId)
            medicoes.findByDispositivoIdOrderByInstanteDesc(dispositivoId, pagina)
        }
        return lista.map { it.paraResposta() }
    }

    private fun MedicaoRequest.leitura(dispositivo: Dispositivo) =
        LeituraDoMedidor(dispositivo.codigoMqtt, tensao, corrente, potencia, energiaKwh)

    private fun provisionar(codigo: String): Dispositivo? {
        val comodo = comodos.findFirstByOrderByIdAsc() ?: return null
        return dispositivos.save(Dispositivo(comodo = comodo, nome = "Medidor $codigo", codigoMqtt = codigo))
    }

    private fun gravar(medicao: Medicao, dispositivo: Dispositivo, leitura: LeituraDoMedidor, origem: OrigemMedicao): Medicao {
        val anterior = medicoes.findTopByDispositivoIdOrderByInstanteDesc(dispositivo.id)
        if (anterior?.id != medicao.id && linhaDeBase.reiniciou(anterior?.energiaKwh, leitura.energiaKwh)) {
            log.info("Contador de energia reiniciou no dispositivo {}", dispositivo.codigoMqtt)
        }
        val tarifa = tarifas.vigentes(dispositivo.comodo.residencia.id, LocalDate.now(ZoneOffset.UTC)).firstOrNull()
        medicao.dispositivo = dispositivo
        medicao.tensao = leitura.tensao
        medicao.corrente = leitura.corrente
        medicao.potencia = leitura.potencia
        medicao.energiaKwh = leitura.energiaKwh
        medicao.custo = calculadora.calcular(leitura.energiaKwh, tarifa?.valorKwh ?: BigDecimal.ONE)
        medicao.origem = origem
        val salva = medicoes.save(medicao)
        alertas.findByDispositivoIdAndAtivoTrue(dispositivo.id).forEach { alerta ->
            alerta.disparado = avaliador.disparou(alerta.grandeza, alerta.limiar, salva.tensao, salva.corrente, salva.potencia)
        }
        return salva
    }

    private fun carregar(id: Long, usuarioId: Long): Medicao {
        val medicao = medicoes.findById(id).orElseThrow { RecursoNaoEncontradoException("Medição não encontrada") }
        donos.dispositivo(medicao.dispositivo.id, usuarioId)
        return medicao
    }

    private companion object {
        val log = LoggerFactory.getLogger(MedicaoService::class.java)
    }
}

@Service
class TarifaService(
    private val tarifas: TarifaRepository,
    private val donos: Donos,
    private val sessao: SessaoAtual,
    private val transacao: Transacao,
) {
    suspend fun listar(residenciaId: Long?): List<TarifaResponse> = io {
        val usuarioId = sessao.id()
        transacao.ler {
            val lista = if (residenciaId == null) {
                tarifas.findByResidenciaUsuarioId(usuarioId)
            } else {
                donos.residencia(residenciaId, usuarioId)
                tarifas.findByResidenciaIdAndResidenciaUsuarioId(residenciaId, usuarioId)
            }
            lista.map { it.paraResposta() }
        }
    }

    suspend fun buscar(id: Long): TarifaResponse = io {
        val usuarioId = sessao.id()
        transacao.ler { carregar(id, usuarioId).paraResposta() }
    }

    suspend fun criar(pedido: TarifaRequest): TarifaResponse = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            validar(pedido)
            val residencia = donos.residencia(pedido.residenciaId, usuarioId)
            tarifas.save(
                Tarifa(
                    residencia = residencia,
                    valorKwh = pedido.valorKwh,
                    vigenciaInicio = pedido.vigenciaInicio,
                    vigenciaFim = pedido.vigenciaFim,
                ),
            ).paraResposta()
        }
    }

    suspend fun atualizar(id: Long, pedido: TarifaRequest): TarifaResponse = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            validar(pedido)
            val tarifa = carregar(id, usuarioId)
            tarifa.residencia = donos.residencia(pedido.residenciaId, usuarioId)
            tarifa.valorKwh = pedido.valorKwh
            tarifa.vigenciaInicio = pedido.vigenciaInicio
            tarifa.vigenciaFim = pedido.vigenciaFim
            tarifa.paraResposta()
        }
    }

    suspend fun excluir(id: Long) = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            carregar(id, usuarioId)
            tarifas.deleteById(id)
        }
    }

    private fun carregar(id: Long, usuarioId: Long): Tarifa {
        val tarifa = tarifas.findById(id).orElseThrow { RecursoNaoEncontradoException("Tarifa não encontrada") }
        donos.residencia(tarifa.residencia.id, usuarioId)
        return tarifa
    }

    private fun validar(pedido: TarifaRequest) {
        if (pedido.valorKwh.signum() < 0) throw RegraDeNegocioException("Tarifa não pode ser negativa")
        if (pedido.vigenciaFim != null && pedido.vigenciaFim.isBefore(pedido.vigenciaInicio)) {
            throw RegraDeNegocioException("Vigência final anterior à inicial")
        }
    }
}

@Service
class AlertaService(
    private val alertas: AlertaRepository,
    private val donos: Donos,
    private val sessao: SessaoAtual,
    private val transacao: Transacao,
) {
    suspend fun listar(dispositivoId: Long?): List<AlertaResponse> = io {
        val usuarioId = sessao.id()
        transacao.ler {
            val lista = if (dispositivoId == null) {
                alertas.findByDispositivoComodoResidenciaUsuarioId(usuarioId)
            } else {
                donos.dispositivo(dispositivoId, usuarioId)
                alertas.findByDispositivoIdAndDispositivoComodoResidenciaUsuarioId(dispositivoId, usuarioId)
            }
            lista.map { it.paraResposta() }
        }
    }

    suspend fun buscar(id: Long): AlertaResponse = io {
        val usuarioId = sessao.id()
        transacao.ler { carregar(id, usuarioId).paraResposta() }
    }

    suspend fun criar(pedido: AlertaRequest): AlertaResponse = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            val dispositivo = donos.dispositivo(pedido.dispositivoId, usuarioId)
            alertas.save(
                Alerta(
                    dispositivo = dispositivo,
                    grandeza = pedido.grandeza,
                    limiar = pedido.limiar,
                    mensagem = pedido.mensagem.trim(),
                    ativo = pedido.ativo,
                ),
            ).paraResposta()
        }
    }

    suspend fun atualizar(id: Long, pedido: AlertaRequest): AlertaResponse = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            val alerta = carregar(id, usuarioId)
            alerta.dispositivo = donos.dispositivo(pedido.dispositivoId, usuarioId)
            alerta.grandeza = pedido.grandeza
            alerta.limiar = pedido.limiar
            alerta.mensagem = pedido.mensagem.trim()
            alerta.ativo = pedido.ativo
            alerta.paraResposta()
        }
    }

    suspend fun excluir(id: Long) = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            carregar(id, usuarioId)
            alertas.deleteById(id)
        }
    }

    private fun carregar(id: Long, usuarioId: Long): Alerta {
        val alerta = alertas.findById(id).orElseThrow { RecursoNaoEncontradoException("Alerta não encontrado") }
        donos.dispositivo(alerta.dispositivo.id, usuarioId)
        return alerta
    }
}

@Service
class MetaConsumoService(
    private val metas: MetaConsumoRepository,
    private val medicoes: MedicaoRepository,
    private val donos: Donos,
    private val sessao: SessaoAtual,
    private val transacao: Transacao,
    private val consumo: ConsumoDoPeriodo = ConsumoDoPeriodo(),
) {
    suspend fun listar(residenciaId: Long?): List<MetaConsumoResponse> = io {
        val usuarioId = sessao.id()
        transacao.ler {
            val lista = if (residenciaId == null) {
                metas.findByResidenciaUsuarioId(usuarioId)
            } else {
                donos.residencia(residenciaId, usuarioId)
                metas.findByResidenciaIdAndResidenciaUsuarioId(residenciaId, usuarioId)
            }
            lista.map { resposta(it) }
        }
    }

    suspend fun buscar(id: Long): MetaConsumoResponse = io {
        val usuarioId = sessao.id()
        transacao.ler { resposta(carregar(id, usuarioId)) }
    }

    suspend fun criar(pedido: MetaConsumoRequest): MetaConsumoResponse = io {
        val usuarioId = sessao.id()
        transacao.escrever { resposta(salvar(usuarioId, MetaConsumo(), pedido)) }
    }

    suspend fun atualizar(id: Long, pedido: MetaConsumoRequest): MetaConsumoResponse = io {
        val usuarioId = sessao.id()
        transacao.escrever { resposta(salvar(usuarioId, carregar(id, usuarioId), pedido)) }
    }

    private fun resposta(meta: MetaConsumo): MetaConsumoResponse {
        val leituras = medicoes.doPeriodo(meta.residencia.id, inicioDoDia(meta.inicio), inicioDoDia(meta.fim.plusDays(1)))
        return meta.paraResposta(consumo.diario(leituras.map { it.paraLeituraEnergia() }).sum())
    }

    suspend fun excluir(id: Long) = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            carregar(id, usuarioId)
            metas.deleteById(id)
        }
    }

    private fun salvar(usuarioId: Long, meta: MetaConsumo, pedido: MetaConsumoRequest): MetaConsumo {
        if (pedido.fim.isBefore(pedido.inicio)) throw RegraDeNegocioException("Fim da meta anterior ao início")
        meta.residencia = donos.residencia(pedido.residenciaId, usuarioId)
        meta.limiteKwh = pedido.limiteKwh
        meta.inicio = pedido.inicio
        meta.fim = pedido.fim
        return metas.save(meta)
    }

    private fun carregar(id: Long, usuarioId: Long): MetaConsumo {
        val meta = metas.findById(id).orElseThrow { RecursoNaoEncontradoException("Meta não encontrada") }
        donos.residencia(meta.residencia.id, usuarioId)
        return meta
    }
}

@Service
class RelatorioService(
    private val relatorios: RelatorioRepository,
    private val medicoes: MedicaoRepository,
    private val tarifas: TarifaRepository,
    private val donos: Donos,
    private val sessao: SessaoAtual,
    private val transacao: Transacao,
    private val previsor: PrevisorDeConsumo = PrevisorDeConsumo(),
    private val consumo: ConsumoDoPeriodo = ConsumoDoPeriodo(),
    private val calculadora: CalculadoraDeCusto = CalculadoraDeCusto(),
) {
    suspend fun listar(residenciaId: Long?): List<RelatorioResponse> = io {
        val usuarioId = sessao.id()
        transacao.ler {
            relatorios.findByResidenciaUsuarioId(usuarioId)
                .filter { residenciaId == null || it.residencia.id == residenciaId }
                .map { it.paraResposta() }
        }
    }

    suspend fun buscar(id: Long): RelatorioResponse = io {
        val usuarioId = sessao.id()
        transacao.ler { carregar(id, usuarioId).paraResposta() }
    }

    suspend fun criar(pedido: RelatorioRequest): RelatorioResponse = io {
        val usuarioId = sessao.id()
        transacao.escrever { aplicar(usuarioId, Relatorio(criadoEm = Instant.now()), pedido).paraResposta() }
    }

    suspend fun atualizar(id: Long, pedido: RelatorioRequest): RelatorioResponse = io {
        val usuarioId = sessao.id()
        transacao.escrever { aplicar(usuarioId, carregar(id, usuarioId), pedido).paraResposta() }
    }

    suspend fun excluir(id: Long) = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            carregar(id, usuarioId)
            relatorios.deleteById(id)
        }
    }

    suspend fun gerar(pedido: GerarRelatorioRequest): RelatorioResponse = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            if (pedido.fim.isBefore(pedido.inicio)) throw RegraDeNegocioException("Fim do relatório anterior ao início")
            val residencia = donos.residencia(pedido.residenciaId, usuarioId)
            val leituras = medicoes.doPeriodo(residencia.id, inicioDoDia(pedido.inicio), inicioDoDia(pedido.fim.plusDays(1)))
            val diario = consumo.diario(leituras.map { it.paraLeituraEnergia() })
            val total = diario.sum()
            val tarifa = tarifas.vigentes(residencia.id, pedido.fim).firstOrNull()?.valorKwh ?: BigDecimal.ONE
            val salvo = relatorios.save(
                Relatorio(
                    residencia = residencia,
                    inicio = pedido.inicio,
                    fim = pedido.fim,
                    consumoKwh = total,
                    custoTotal = calculadora.calcular(total, tarifa),
                    previsaoKwh = previsor.preverProximo(diario),
                    criadoEm = Instant.now(),
                ),
            )
            salvo.paraResposta()
        }
    }

    private fun aplicar(usuarioId: Long, relatorio: Relatorio, pedido: RelatorioRequest): Relatorio {
        if (pedido.fim.isBefore(pedido.inicio)) throw RegraDeNegocioException("Fim do relatório anterior ao início")
        relatorio.residencia = donos.residencia(pedido.residenciaId, usuarioId)
        relatorio.inicio = pedido.inicio
        relatorio.fim = pedido.fim
        relatorio.consumoKwh = pedido.consumoKwh
        relatorio.custoTotal = pedido.custoTotal
        relatorio.previsaoKwh = pedido.previsaoKwh
        if (relatorio.criadoEm == Instant.EPOCH) relatorio.criadoEm = Instant.now()
        return relatorios.save(relatorio)
    }

    private fun carregar(id: Long, usuarioId: Long): Relatorio {
        val relatorio = relatorios.findById(id).orElseThrow { RecursoNaoEncontradoException("Relatório não encontrado") }
        donos.residencia(relatorio.residencia.id, usuarioId)
        return relatorio
    }
}

private fun inicioDoDia(dia: LocalDate): Instant = dia.atStartOfDay().toInstant(ZoneOffset.UTC)

private fun Medicao.paraLeituraEnergia() =
    LeituraEnergia(instante.atZone(ZoneOffset.UTC).toLocalDate().toString(), instante.toEpochMilli(), energiaKwh)
