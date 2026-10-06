package com.ampera.infrastructure.persistence

import com.ampera.domain.Grandeza
import com.ampera.domain.OrigemMedicao
import com.ampera.domain.TipoSensor
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "usuario")
class Usuario(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    var nome: String = "",
    @Column(nullable = false, unique = true)
    var email: String = "",
    @Column(name = "senha_hash", nullable = false)
    var senhaHash: String = "",
)

@Entity
@Table(name = "residencia")
class Residencia(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    var usuario: Usuario = Usuario(),
    var nome: String = "",
    var endereco: String = "",
)

@Entity
@Table(name = "comodo")
class Comodo(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "residencia_id")
    var residencia: Residencia = Residencia(),
    var nome: String = "",
)

@Entity
@Table(name = "dispositivo")
class Dispositivo(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comodo_id")
    var comodo: Comodo = Comodo(),
    var nome: String = "",
    @Column(name = "codigo_mqtt", nullable = false, unique = true)
    var codigoMqtt: String = "",
)

@Entity
@Table(name = "sensor")
class Sensor(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispositivo_id")
    var dispositivo: Dispositivo = Dispositivo(),
    @Enumerated(EnumType.STRING)
    var tipo: TipoSensor = TipoSensor.TENSAO,
    var unidade: String = "",
)

@Entity
@Table(name = "medicao")
class Medicao(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispositivo_id")
    var dispositivo: Dispositivo = Dispositivo(),
    var tensao: Double = 0.0,
    var corrente: Double = 0.0,
    var potencia: Double = 0.0,
    @Column(name = "energia_kwh")
    var energiaKwh: Double = 0.0,
    var custo: BigDecimal = BigDecimal.ZERO,
    var instante: Instant = Instant.EPOCH,
    @Enumerated(EnumType.STRING)
    var origem: OrigemMedicao = OrigemMedicao.MANUAL,
)

@Entity
@Table(name = "tarifa")
class Tarifa(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "residencia_id")
    var residencia: Residencia = Residencia(),
    @Column(name = "valor_kwh")
    var valorKwh: BigDecimal = BigDecimal.ZERO,
    @Column(name = "vigencia_inicio")
    var vigenciaInicio: LocalDate = LocalDate.EPOCH,
    @Column(name = "vigencia_fim")
    var vigenciaFim: LocalDate? = null,
)

@Entity
@Table(name = "alerta")
class Alerta(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispositivo_id")
    var dispositivo: Dispositivo = Dispositivo(),
    @Enumerated(EnumType.STRING)
    var grandeza: Grandeza = Grandeza.POTENCIA,
    var limiar: Double = 0.0,
    var mensagem: String = "",
    var ativo: Boolean = true,
    var disparado: Boolean = false,
)

@Entity
@Table(name = "meta_consumo")
class MetaConsumo(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "residencia_id")
    var residencia: Residencia = Residencia(),
    @Column(name = "limite_kwh")
    var limiteKwh: Double = 0.0,
    var inicio: LocalDate = LocalDate.EPOCH,
    var fim: LocalDate = LocalDate.EPOCH,
)

@Entity
@Table(name = "relatorio")
class Relatorio(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "residencia_id")
    var residencia: Residencia = Residencia(),
    var inicio: LocalDate = LocalDate.EPOCH,
    var fim: LocalDate = LocalDate.EPOCH,
    @Column(name = "consumo_kwh")
    var consumoKwh: Double = 0.0,
    @Column(name = "custo_total")
    var custoTotal: BigDecimal = BigDecimal.ZERO,
    @Column(name = "previsao_kwh")
    var previsaoKwh: Double = 0.0,
    @Column(name = "criado_em")
    var criadoEm: Instant = Instant.EPOCH,
)
