package com.ampera.infrastructure.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.time.LocalDate

interface UsuarioRepository : JpaRepository<Usuario, Long> {
    fun findByEmail(email: String): Usuario?
}

interface ResidenciaRepository : JpaRepository<Residencia, Long> {
    fun findByUsuarioId(usuarioId: Long): List<Residencia>
}

interface ComodoRepository : JpaRepository<Comodo, Long> {
    fun findByResidenciaUsuarioId(usuarioId: Long): List<Comodo>
    fun findByResidenciaIdAndResidenciaUsuarioId(residenciaId: Long, usuarioId: Long): List<Comodo>
    fun findFirstByOrderByIdAsc(): Comodo?
}

interface DispositivoRepository : JpaRepository<Dispositivo, Long> {
    fun findByCodigoMqtt(codigoMqtt: String): Dispositivo?
    fun findByComodoResidenciaUsuarioId(usuarioId: Long): List<Dispositivo>
    fun findByComodoIdAndComodoResidenciaUsuarioId(comodoId: Long, usuarioId: Long): List<Dispositivo>
}

interface SensorRepository : JpaRepository<Sensor, Long> {
    fun findByDispositivoComodoResidenciaUsuarioId(usuarioId: Long): List<Sensor>
    fun findByDispositivoIdAndDispositivoComodoResidenciaUsuarioId(dispositivoId: Long, usuarioId: Long): List<Sensor>
}

interface MedicaoRepository : JpaRepository<Medicao, Long> {
    fun findByDispositivoIdOrderByInstanteDesc(dispositivoId: Long, pageable: Pageable): List<Medicao>
    fun findTopByDispositivoIdOrderByInstanteDesc(dispositivoId: Long): Medicao?
    fun findByDispositivoComodoResidenciaUsuarioIdOrderByInstanteDesc(usuarioId: Long, pageable: Pageable): List<Medicao>

    @Query(
        """
        select m from Medicao m
        where m.dispositivo.comodo.residencia.id = :residenciaId
          and m.instante >= :inicio
          and m.instante < :fim
        order by m.instante asc
        """,
    )
    fun doPeriodo(residenciaId: Long, inicio: Instant, fim: Instant): List<Medicao>
}

interface TarifaRepository : JpaRepository<Tarifa, Long> {
    fun findByResidenciaUsuarioId(usuarioId: Long): List<Tarifa>
    fun findByResidenciaIdAndResidenciaUsuarioId(residenciaId: Long, usuarioId: Long): List<Tarifa>

    @Query(
        """
        select t from Tarifa t
        where t.residencia.id = :residenciaId
          and t.vigenciaInicio <= :dia
          and (t.vigenciaFim is null or t.vigenciaFim >= :dia)
        order by t.vigenciaInicio desc
        """,
    )
    fun vigentes(residenciaId: Long, dia: LocalDate): List<Tarifa>
}

interface AlertaRepository : JpaRepository<Alerta, Long> {
    fun findByDispositivoIdAndAtivoTrue(dispositivoId: Long): List<Alerta>
    fun findByDispositivoComodoResidenciaUsuarioId(usuarioId: Long): List<Alerta>
    fun findByDispositivoIdAndDispositivoComodoResidenciaUsuarioId(dispositivoId: Long, usuarioId: Long): List<Alerta>
}

interface MetaConsumoRepository : JpaRepository<MetaConsumo, Long> {
    fun findByResidenciaUsuarioId(usuarioId: Long): List<MetaConsumo>
    fun findByResidenciaIdAndResidenciaUsuarioId(residenciaId: Long, usuarioId: Long): List<MetaConsumo>
}

interface RelatorioRepository : JpaRepository<Relatorio, Long> {
    fun findByResidenciaUsuarioId(usuarioId: Long): List<Relatorio>
}
