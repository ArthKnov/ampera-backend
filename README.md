# ampera-backend

API REST em Spring Boot com Kotlin: JWT, OpenAPI, Flyway, PostgreSQL e assinatura MQTT do medidor ESP32.

## Subir banco e broker

Com Docker:

```
docker compose up -d
```

Sem Docker (Windows), PostgreSQL portátil e Mosquitto nativo:

```
powershell -ExecutionPolicy Bypass -File scripts\instalar-local.ps1   # uma vez
powershell -ExecutionPolicy Bypass -File scripts\subir-local.ps1
```

O Mosquitto usa `docker/mosquitto/mosquitto.conf`, que escuta em todas as interfaces na porta 1883 para o ESP32 alcançar o PC pela rede.

## Rodar a API

```
./gradlew bootRun
```

- Swagger: `http://localhost:8080/swagger-ui.html`
- Login de demonstração: `demo@ampera.local` / `demo123`. O seed fica em `db/seed` e só roda no perfil `local`, que é o padrão.
- Rotas em `/api/**`, exceto `/api/auth/**`, exigem `Authorization: Bearer <token>`. Cada residência, e tudo o que pende dela, só é acessível pelo dono.

## MQTT

O backend assina `ampera/+/medicao`. Teste sem o ESP32:

```
mosquitto_pub -h localhost -t ampera/medidor-sala/medicao -m '{"deviceId":"medidor-sala","tensao":127.2,"corrente":0.84,"potencia":107.1,"energiaKwh":0.0123}'
```

Cada mensagem vira uma `Medicao` com origem `MQTT`, custo pela tarifa vigente e reavaliação dos alertas do dispositivo. As medições alimentam o progresso da meta de consumo e a previsão por regressão linear em `POST /api/relatorios/gerar`.
