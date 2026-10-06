package com.ampera.api

import com.ampera.application.AlertaService
import com.ampera.application.ComodoService
import com.ampera.application.DispositivoService
import com.ampera.application.MedicaoService
import com.ampera.application.MetaConsumoService
import com.ampera.application.RelatorioService
import com.ampera.application.ResidenciaService
import com.ampera.application.SensorService
import com.ampera.application.TarifaService
import com.ampera.application.UsuarioService
import com.ampera.domain.AcessoNegadoException
import com.ampera.domain.RecursoNaoEncontradoException
import com.ampera.domain.RegraDeNegocioException
import com.ampera.infrastructure.security.JwtService
import com.ampera.infrastructure.security.UsuarioPrincipal
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import jakarta.validation.Valid
import kotlinx.coroutines.flow.toList
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice

const val USUARIO_AUTENTICADO = "hasRole('USER')"

@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(Info().title("Ampéra API").version("1.0").description("Monitoramento de energia"))
        .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
        .components(
            Components().addSecuritySchemes(
                "bearerAuth",
                SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"),
            ),
        )
}

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(RecursoNaoEncontradoException::class)
    fun naoEncontrado(ex: RecursoNaoEncontradoException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErroResponse(ex.message))

    @ExceptionHandler(AcessoNegadoException::class)
    fun negado(ex: AcessoNegadoException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErroResponse(ex.message))

    @ExceptionHandler(RegraDeNegocioException::class)
    fun regra(ex: RegraDeNegocioException) =
        ResponseEntity.badRequest().body(ErroResponse(ex.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validacao(ex: MethodArgumentNotValidException): ResponseEntity<ErroResponse> {
        val texto = ex.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.badRequest().body(ErroResponse(texto))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun corpoInvalido(@Suppress("UNUSED_PARAMETER") ex: HttpMessageNotReadableException) =
        ResponseEntity.badRequest().body(ErroResponse("Corpo inválido"))

    @ExceptionHandler(BadCredentialsException::class, AuthenticationException::class)
    fun credenciais(@Suppress("UNUSED_PARAMETER") ex: AuthenticationException) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErroResponse("E-mail ou senha inválidos"))
}

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val autenticacao: AuthenticationManager,
    private val jwt: JwtService,
    private val usuarios: UsuarioService,
) {
    @PostMapping("/login")
    suspend fun login(@Valid @RequestBody pedido: LoginRequest): LoginResponse {
        val auth = autenticacao.authenticate(UsernamePasswordAuthenticationToken(pedido.email, pedido.senha))
        val principal = auth.principal as UsuarioPrincipal
        return LoginResponse(jwt.gerar(principal.id, principal.username), usuarios.buscar(principal.id))
    }

    @PostMapping("/register")
    suspend fun registrar(@Valid @RequestBody pedido: UsuarioRequest): UsuarioResponse = usuarios.criar(pedido)
}

@RestController
@PreAuthorize(USUARIO_AUTENTICADO)
@RequestMapping("/api/usuarios")
class UsuarioController(private val servico: UsuarioService) {
    @GetMapping
    suspend fun listar() = servico.listar()

    @GetMapping("/{id}")
    suspend fun buscar(@PathVariable id: Long) = servico.buscar(id)

    @PostMapping
    suspend fun criar(@Valid @RequestBody pedido: UsuarioRequest) = servico.criar(pedido)

    @PutMapping("/{id}")
    suspend fun atualizar(@PathVariable id: Long, @Valid @RequestBody pedido: UsuarioRequest) = servico.atualizar(id, pedido)

    @DeleteMapping("/{id}")
    suspend fun excluir(@PathVariable id: Long) = servico.excluir(id)
}

@RestController
@PreAuthorize(USUARIO_AUTENTICADO)
@RequestMapping("/api/residencias")
class ResidenciaController(private val servico: ResidenciaService) {
    @GetMapping
    suspend fun listar() = servico.listar()

    @GetMapping("/{id}")
    suspend fun buscar(@PathVariable id: Long) = servico.buscar(id)

    @PostMapping
    suspend fun criar(@Valid @RequestBody pedido: ResidenciaRequest) = servico.criar(pedido)

    @PutMapping("/{id}")
    suspend fun atualizar(@PathVariable id: Long, @Valid @RequestBody pedido: ResidenciaRequest) = servico.atualizar(id, pedido)

    @DeleteMapping("/{id}")
    suspend fun excluir(@PathVariable id: Long) = servico.excluir(id)
}

@RestController
@PreAuthorize(USUARIO_AUTENTICADO)
@RequestMapping("/api/comodos")
class ComodoController(private val servico: ComodoService) {
    @GetMapping
    suspend fun listar(@RequestParam(required = false) residenciaId: Long?) = servico.listar(residenciaId)

    @GetMapping("/{id}")
    suspend fun buscar(@PathVariable id: Long) = servico.buscar(id)

    @PostMapping
    suspend fun criar(@Valid @RequestBody pedido: ComodoRequest) = servico.criar(pedido)

    @PutMapping("/{id}")
    suspend fun atualizar(@PathVariable id: Long, @Valid @RequestBody pedido: ComodoRequest) = servico.atualizar(id, pedido)

    @DeleteMapping("/{id}")
    suspend fun excluir(@PathVariable id: Long) = servico.excluir(id)
}

@RestController
@PreAuthorize(USUARIO_AUTENTICADO)
@RequestMapping("/api/dispositivos")
class DispositivoController(private val servico: DispositivoService) {
    @GetMapping
    suspend fun listar(@RequestParam(required = false) comodoId: Long?) = servico.listar(comodoId)

    @GetMapping("/{id}")
    suspend fun buscar(@PathVariable id: Long) = servico.buscar(id)

    @PostMapping
    suspend fun criar(@Valid @RequestBody pedido: DispositivoRequest) = servico.criar(pedido)

    @PutMapping("/{id}")
    suspend fun atualizar(@PathVariable id: Long, @Valid @RequestBody pedido: DispositivoRequest) = servico.atualizar(id, pedido)

    @DeleteMapping("/{id}")
    suspend fun excluir(@PathVariable id: Long) = servico.excluir(id)
}

@RestController
@PreAuthorize(USUARIO_AUTENTICADO)
@RequestMapping("/api/sensores")
class SensorController(private val servico: SensorService) {
    @GetMapping
    suspend fun listar(@RequestParam(required = false) dispositivoId: Long?) = servico.listar(dispositivoId)

    @GetMapping("/{id}")
    suspend fun buscar(@PathVariable id: Long) = servico.buscar(id)

    @PostMapping
    suspend fun criar(@Valid @RequestBody pedido: SensorRequest) = servico.criar(pedido)

    @PutMapping("/{id}")
    suspend fun atualizar(@PathVariable id: Long, @Valid @RequestBody pedido: SensorRequest) = servico.atualizar(id, pedido)

    @DeleteMapping("/{id}")
    suspend fun excluir(@PathVariable id: Long) = servico.excluir(id)
}

@RestController
@PreAuthorize(USUARIO_AUTENTICADO)
@RequestMapping("/api/medicoes")
class MedicaoController(private val servico: MedicaoService) {
    @GetMapping
    suspend fun listar(
        @RequestParam(required = false) dispositivoId: Long?,
        @RequestParam(defaultValue = "50") limit: Int,
    ) = servico.recentes(dispositivoId, limit).toList()

    @GetMapping("/{id}")
    suspend fun buscar(@PathVariable id: Long) = servico.buscar(id)

    @PostMapping
    suspend fun criar(@Valid @RequestBody pedido: MedicaoRequest) = servico.criar(pedido)

    @PutMapping("/{id}")
    suspend fun atualizar(@PathVariable id: Long, @Valid @RequestBody pedido: MedicaoRequest) = servico.atualizar(id, pedido)

    @DeleteMapping("/{id}")
    suspend fun excluir(@PathVariable id: Long) = servico.excluir(id)
}

@RestController
@PreAuthorize(USUARIO_AUTENTICADO)
@RequestMapping("/api/tarifas")
class TarifaController(private val servico: TarifaService) {
    @GetMapping
    suspend fun listar(@RequestParam(required = false) residenciaId: Long?) = servico.listar(residenciaId)

    @GetMapping("/{id}")
    suspend fun buscar(@PathVariable id: Long) = servico.buscar(id)

    @PostMapping
    suspend fun criar(@Valid @RequestBody pedido: TarifaRequest) = servico.criar(pedido)

    @PutMapping("/{id}")
    suspend fun atualizar(@PathVariable id: Long, @Valid @RequestBody pedido: TarifaRequest) = servico.atualizar(id, pedido)

    @DeleteMapping("/{id}")
    suspend fun excluir(@PathVariable id: Long) = servico.excluir(id)
}

@RestController
@PreAuthorize(USUARIO_AUTENTICADO)
@RequestMapping("/api/alertas")
class AlertaController(private val servico: AlertaService) {
    @GetMapping
    suspend fun listar(@RequestParam(required = false) dispositivoId: Long?) = servico.listar(dispositivoId)

    @GetMapping("/{id}")
    suspend fun buscar(@PathVariable id: Long) = servico.buscar(id)

    @PostMapping
    suspend fun criar(@Valid @RequestBody pedido: AlertaRequest) = servico.criar(pedido)

    @PutMapping("/{id}")
    suspend fun atualizar(@PathVariable id: Long, @Valid @RequestBody pedido: AlertaRequest) = servico.atualizar(id, pedido)

    @DeleteMapping("/{id}")
    suspend fun excluir(@PathVariable id: Long) = servico.excluir(id)
}

@RestController
@PreAuthorize(USUARIO_AUTENTICADO)
@RequestMapping("/api/metas-consumo")
class MetaConsumoController(private val servico: MetaConsumoService) {
    @GetMapping
    suspend fun listar(@RequestParam(required = false) residenciaId: Long?) = servico.listar(residenciaId)

    @GetMapping("/{id}")
    suspend fun buscar(@PathVariable id: Long) = servico.buscar(id)

    @PostMapping
    suspend fun criar(@Valid @RequestBody pedido: MetaConsumoRequest) = servico.criar(pedido)

    @PutMapping("/{id}")
    suspend fun atualizar(@PathVariable id: Long, @Valid @RequestBody pedido: MetaConsumoRequest) = servico.atualizar(id, pedido)

    @DeleteMapping("/{id}")
    suspend fun excluir(@PathVariable id: Long) = servico.excluir(id)
}

@RestController
@PreAuthorize(USUARIO_AUTENTICADO)
@RequestMapping("/api/relatorios")
class RelatorioController(private val servico: RelatorioService) {
    @GetMapping
    suspend fun listar(@RequestParam(required = false) residenciaId: Long?) = servico.listar(residenciaId)

    @GetMapping("/{id}")
    suspend fun buscar(@PathVariable id: Long) = servico.buscar(id)

    @PostMapping
    suspend fun criar(@Valid @RequestBody pedido: RelatorioRequest) = servico.criar(pedido)

    @PostMapping("/gerar")
    suspend fun gerar(@Valid @RequestBody pedido: GerarRelatorioRequest) = servico.gerar(pedido)

    @PutMapping("/{id}")
    suspend fun atualizar(@PathVariable id: Long, @Valid @RequestBody pedido: RelatorioRequest) = servico.atualizar(id, pedido)

    @DeleteMapping("/{id}")
    suspend fun excluir(@PathVariable id: Long) = servico.excluir(id)
}
