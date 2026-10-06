package com.ampera.application

import com.ampera.domain.AcessoNegadoException
import com.ampera.domain.AcessoPorDono
import com.ampera.domain.RecursoNaoEncontradoException
import com.ampera.infrastructure.persistence.Comodo
import com.ampera.infrastructure.persistence.ComodoRepository
import com.ampera.infrastructure.persistence.Dispositivo
import com.ampera.infrastructure.persistence.DispositivoRepository
import com.ampera.infrastructure.persistence.Residencia
import com.ampera.infrastructure.persistence.ResidenciaRepository
import com.ampera.infrastructure.security.UsuarioPrincipal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.coroutines.CoroutineContext

internal suspend fun <T> io(bloco: () -> T): T {
    val contexto = SecurityContextHolder.getContext()
    return withContext(Dispatchers.IO + ContextoDeSeguranca(contexto)) { bloco() }
}

private class ContextoDeSeguranca(
    private val contexto: SecurityContext,
) : ThreadContextElement<SecurityContext?> {
    companion object Key : CoroutineContext.Key<ContextoDeSeguranca>

    override val key: CoroutineContext.Key<ContextoDeSeguranca> = Key

    override fun updateThreadContext(context: CoroutineContext): SecurityContext? {
        val anterior = SecurityContextHolder.getContext()
        SecurityContextHolder.setContext(contexto)
        return anterior
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: SecurityContext?) {
        SecurityContextHolder.setContext(oldState ?: SecurityContextHolder.createEmptyContext())
    }
}

@Component
class Transacao(tx: PlatformTransactionManager) {
    private val escrita = TransactionTemplate(tx)
    private val leitura = TransactionTemplate(tx).apply { isReadOnly = true }

    fun <T> ler(bloco: () -> T): T = leitura.execute { bloco() }!!

    fun <T> escrever(bloco: () -> T): T = escrita.execute { bloco() }!!
}

@Component
class SessaoAtual {
    fun id(): Long {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        if (principal is UsuarioPrincipal) return principal.id
        throw AcessoNegadoException("Não autenticado")
    }
}

@Component
class Donos(
    private val residencias: ResidenciaRepository,
    private val comodos: ComodoRepository,
    private val dispositivos: DispositivoRepository,
    private val acesso: AcessoPorDono = AcessoPorDono(),
) {
    fun residencia(id: Long, usuarioId: Long): Residencia {
        val residencia = residencias.findById(id).orElseThrow {
            RecursoNaoEncontradoException("Residência não encontrada")
        }
        acesso.exigir(residencia.usuario.id, usuarioId)
        return residencia
    }

    fun comodo(id: Long, usuarioId: Long): Comodo {
        val comodo = comodos.findById(id).orElseThrow { RecursoNaoEncontradoException("Cômodo não encontrado") }
        acesso.exigir(comodo.residencia.usuario.id, usuarioId)
        return comodo
    }

    fun dispositivo(id: Long, usuarioId: Long): Dispositivo {
        val dispositivo = dispositivos.findById(id).orElseThrow {
            RecursoNaoEncontradoException("Dispositivo não encontrado")
        }
        acesso.exigir(dispositivo.comodo.residencia.usuario.id, usuarioId)
        return dispositivo
    }
}
