package com.ampera.infrastructure.mqtt

import com.ampera.application.MedicaoService
import com.ampera.domain.LeituraDoMedidor
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.integration.channel.DirectChannel
import org.springframework.integration.core.MessageProducer
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory
import org.springframework.integration.mqtt.core.MqttPahoClientFactory
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessageHandler
import org.springframework.stereotype.Component

data class MedicaoMqtt(
    val deviceId: String = "",
    val tensao: Double = 0.0,
    val corrente: Double = 0.0,
    val potencia: Double = 0.0,
    val energiaKwh: Double = 0.0,
) {
    fun paraLeitura() = LeituraDoMedidor(deviceId.trim(), tensao, corrente, potencia, energiaKwh)
}

@Component
class MedicaoMqttListener(
    private val medicoes: MedicaoService,
    private val json: ObjectMapper,
) {
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val leituras = MutableSharedFlow<LeituraDoMedidor>(extraBufferCapacity = 256)

    init {
        escopo.launch {
            leituras.collect { leitura ->
                runCatching { medicoes.registrarDoMedidor(leitura) }
                    .onFailure { log.error("Falha ao gravar medição de {}", leitura.codigoMqtt, it) }
            }
        }
    }

    fun receber(corpo: String) {
        val mensagem = runCatching { json.readValue(corpo, MedicaoMqtt::class.java) }.getOrNull()
        if (mensagem == null || mensagem.deviceId.isBlank()) {
            log.warn("Mensagem MQTT ignorada: {}", corpo.take(200))
            return
        }
        if (!leituras.tryEmit(mensagem.paraLeitura())) {
            log.warn("Fila de medições cheia; leitura de {} descartada", mensagem.deviceId)
        }
    }

    @PreDestroy
    fun parar() = escopo.cancel()

    private companion object {
        val log = LoggerFactory.getLogger(MedicaoMqttListener::class.java)
    }
}

@Configuration
class MqttConfig {
    @Bean
    fun mqttClientFactory(@Value("\${ampera.mqtt.url}") url: String): MqttPahoClientFactory {
        val fabrica = DefaultMqttPahoClientFactory()
        val opcoes = MqttConnectOptions()
        opcoes.serverURIs = arrayOf(url)
        opcoes.isAutomaticReconnect = true
        opcoes.connectionTimeout = 10
        fabrica.connectionOptions = opcoes
        return fabrica
    }

    @Bean
    fun mqttInputChannel(): MessageChannel = DirectChannel()

    @Bean
    fun mqttInbound(
        factory: MqttPahoClientFactory,
        @Value("\${ampera.mqtt.topico}") topico: String,
        @Value("\${ampera.mqtt.client-id}") clientId: String,
    ): MessageProducer {
        val adapter = MqttPahoMessageDrivenChannelAdapter(clientId, factory, topico)
        adapter.setCompletionTimeout(5000)
        adapter.setConverter(DefaultPahoMessageConverter())
        adapter.setQos(1)
        adapter.outputChannel = mqttInputChannel()
        return adapter
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    fun mqttHandler(listener: MedicaoMqttListener): MessageHandler = MessageHandler { mensagem ->
        listener.receber(mensagem.payload?.toString().orEmpty())
    }
}
