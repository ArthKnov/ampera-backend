package com.ampera.application

import com.ampera.api.ComodoRequest
import com.ampera.api.ComodoResponse
import com.ampera.api.DispositivoRequest
import com.ampera.api.DispositivoResponse
import com.ampera.api.ResidenciaRequest
import com.ampera.api.ResidenciaResponse
import com.ampera.api.UsuarioRequest
import com.ampera.api.UsuarioResponse
import com.ampera.api.paraResposta
import com.ampera.domain.AcessoNegadoException
import com.ampera.domain.RecursoNaoEncontradoException
import com.ampera.domain.RegraDeNegocioException
import com.ampera.infrastructure.persistence.Comodo
import com.ampera.infrastructure.persistence.ComodoRepository
import com.ampera.infrastructure.persistence.Dispositivo
import com.ampera.infrastructure.persistence.DispositivoRepository
import com.ampera.infrastructure.persistence.Residencia
import com.ampera.infrastructure.persistence.ResidenciaRepository
import com.ampera.infrastructure.persistence.Usuario
import com.ampera.infrastructure.persistence.UsuarioRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UsuarioService(
    private val usuarios: UsuarioRepository,
    private val sessao: SessaoAtual,
    private val transacao: Transacao,
    private val senhas: PasswordEncoder,
) {
    suspend fun listar(): List<UsuarioResponse> = io {
        transacao.ler { usuarios.findAll().map { it.paraResposta() } }
    }

    suspend fun buscar(id: Long): UsuarioResponse = io {
        transacao.ler { usuarios.findById(id).orElseThrow { RecursoNaoEncontradoException("Usuário não encontrado") }.paraResposta() }
    }

    suspend fun criar(pedido: UsuarioRequest): UsuarioResponse = io {
        val senha = pedido.senha?.takeIf { it.length >= 6 }
            ?: throw RegraDeNegocioException("Senha deve ter ao menos 6 caracteres")
        transacao.escrever {
            if (usuarios.findByEmail(pedido.email) != null) throw RegraDeNegocioException("E-mail já cadastrado")
            usuarios.save(Usuario(nome = pedido.nome.trim(), email = pedido.email.trim(), senhaHash = senhas.encode(senha))).paraResposta()
        }
    }

    suspend fun atualizar(id: Long, pedido: UsuarioRequest): UsuarioResponse = io {
        val atual = sessao.id()
        if (atual != id) throw AcessoNegadoException("Só é possível alterar o próprio usuário")
        transacao.escrever {
            val usuario = usuarios.findById(id).orElseThrow { RecursoNaoEncontradoException("Usuário não encontrado") }
            usuarios.findByEmail(pedido.email)?.let { if (it.id != id) throw RegraDeNegocioException("E-mail já cadastrado") }
            usuario.nome = pedido.nome.trim()
            usuario.email = pedido.email.trim()
            pedido.senha?.takeIf { it.isNotBlank() }?.let {
                if (it.length < 6) throw RegraDeNegocioException("Senha deve ter ao menos 6 caracteres")
                usuario.senhaHash = senhas.encode(it)
            }
            usuario.paraResposta()
        }
    }

    suspend fun excluir(id: Long) = io {
        if (sessao.id() != id) throw AcessoNegadoException("Só é possível excluir o próprio usuário")
        transacao.escrever {
            if (!usuarios.existsById(id)) throw RecursoNaoEncontradoException("Usuário não encontrado")
            usuarios.deleteById(id)
        }
    }
}

@Service
class ResidenciaService(
    private val residencias: ResidenciaRepository,
    private val usuarios: UsuarioRepository,
    private val donos: Donos,
    private val sessao: SessaoAtual,
    private val transacao: Transacao,
) {
    suspend fun listar(): List<ResidenciaResponse> = io {
        val usuarioId = sessao.id()
        transacao.ler { residencias.findByUsuarioId(usuarioId).map { it.paraResposta() } }
    }

    suspend fun buscar(id: Long): ResidenciaResponse = io {
        val usuarioId = sessao.id()
        transacao.ler { donos.residencia(id, usuarioId).paraResposta() }
    }

    suspend fun criar(pedido: ResidenciaRequest): ResidenciaResponse = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            val usuario = usuarios.findById(usuarioId).orElseThrow { RecursoNaoEncontradoException("Usuário não encontrado") }
            residencias.save(Residencia(usuario = usuario, nome = pedido.nome.trim(), endereco = pedido.endereco.trim())).paraResposta()
        }
    }

    suspend fun atualizar(id: Long, pedido: ResidenciaRequest): ResidenciaResponse = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            val residencia = donos.residencia(id, usuarioId)
            residencia.nome = pedido.nome.trim()
            residencia.endereco = pedido.endereco.trim()
            residencia.paraResposta()
        }
    }

    suspend fun excluir(id: Long) = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            donos.residencia(id, usuarioId)
            residencias.deleteById(id)
        }
    }
}

@Service
class ComodoService(
    private val comodos: ComodoRepository,
    private val donos: Donos,
    private val sessao: SessaoAtual,
    private val transacao: Transacao,
) {
    suspend fun listar(residenciaId: Long?): List<ComodoResponse> = io {
        val usuarioId = sessao.id()
        transacao.ler {
            val lista = if (residenciaId == null) {
                comodos.findByResidenciaUsuarioId(usuarioId)
            } else {
                donos.residencia(residenciaId, usuarioId)
                comodos.findByResidenciaIdAndResidenciaUsuarioId(residenciaId, usuarioId)
            }
            lista.map { it.paraResposta() }
        }
    }

    suspend fun buscar(id: Long): ComodoResponse = io {
        val usuarioId = sessao.id()
        transacao.ler { donos.comodo(id, usuarioId).paraResposta() }
    }

    suspend fun criar(pedido: ComodoRequest): ComodoResponse = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            val residencia = donos.residencia(pedido.residenciaId, usuarioId)
            comodos.save(Comodo(residencia = residencia, nome = pedido.nome.trim())).paraResposta()
        }
    }

    suspend fun atualizar(id: Long, pedido: ComodoRequest): ComodoResponse = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            val comodo = donos.comodo(id, usuarioId)
            comodo.residencia = donos.residencia(pedido.residenciaId, usuarioId)
            comodo.nome = pedido.nome.trim()
            comodo.paraResposta()
        }
    }

    suspend fun excluir(id: Long) = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            donos.comodo(id, usuarioId)
            comodos.deleteById(id)
        }
    }
}

@Service
class DispositivoService(
    private val dispositivos: DispositivoRepository,
    private val donos: Donos,
    private val sessao: SessaoAtual,
    private val transacao: Transacao,
) {
    suspend fun listar(comodoId: Long?): List<DispositivoResponse> = io {
        val usuarioId = sessao.id()
        transacao.ler {
            val lista = if (comodoId == null) {
                dispositivos.findByComodoResidenciaUsuarioId(usuarioId)
            } else {
                donos.comodo(comodoId, usuarioId)
                dispositivos.findByComodoIdAndComodoResidenciaUsuarioId(comodoId, usuarioId)
            }
            lista.map { it.paraResposta() }
        }
    }

    suspend fun buscar(id: Long): DispositivoResponse = io {
        val usuarioId = sessao.id()
        transacao.ler { donos.dispositivo(id, usuarioId).paraResposta() }
    }

    suspend fun criar(pedido: DispositivoRequest): DispositivoResponse = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            if (dispositivos.findByCodigoMqtt(pedido.codigoMqtt) != null) {
                throw RegraDeNegocioException("Código MQTT já usado")
            }
            val comodo = donos.comodo(pedido.comodoId, usuarioId)
            dispositivos.save(
                Dispositivo(comodo = comodo, nome = pedido.nome.trim(), codigoMqtt = pedido.codigoMqtt.trim()),
            ).paraResposta()
        }
    }

    suspend fun atualizar(id: Long, pedido: DispositivoRequest): DispositivoResponse = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            val dispositivo = donos.dispositivo(id, usuarioId)
            dispositivos.findByCodigoMqtt(pedido.codigoMqtt)?.let {
                if (it.id != id) throw RegraDeNegocioException("Código MQTT já usado")
            }
            dispositivo.comodo = donos.comodo(pedido.comodoId, usuarioId)
            dispositivo.nome = pedido.nome.trim()
            dispositivo.codigoMqtt = pedido.codigoMqtt.trim()
            dispositivo.paraResposta()
        }
    }

    suspend fun excluir(id: Long) = io {
        val usuarioId = sessao.id()
        transacao.escrever {
            donos.dispositivo(id, usuarioId)
            dispositivos.deleteById(id)
        }
    }
}
