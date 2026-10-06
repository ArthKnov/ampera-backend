CREATE TABLE usuario (
    id          BIGSERIAL PRIMARY KEY,
    nome        VARCHAR(120)  NOT NULL,
    email       VARCHAR(180)  NOT NULL UNIQUE,
    senha_hash  VARCHAR(255)  NOT NULL
);

CREATE TABLE residencia (
    id          BIGSERIAL PRIMARY KEY,
    usuario_id  BIGINT        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    nome        VARCHAR(120)  NOT NULL,
    endereco    VARCHAR(255)  NOT NULL
);

CREATE TABLE comodo (
    id             BIGSERIAL PRIMARY KEY,
    residencia_id  BIGINT       NOT NULL REFERENCES residencia (id) ON DELETE CASCADE,
    nome           VARCHAR(120) NOT NULL
);

CREATE TABLE dispositivo (
    id          BIGSERIAL PRIMARY KEY,
    comodo_id   BIGINT       NOT NULL REFERENCES comodo (id) ON DELETE CASCADE,
    nome        VARCHAR(120) NOT NULL,
    codigo_mqtt VARCHAR(80)  NOT NULL UNIQUE
);

CREATE TABLE sensor (
    id              BIGSERIAL PRIMARY KEY,
    dispositivo_id  BIGINT      NOT NULL REFERENCES dispositivo (id) ON DELETE CASCADE,
    tipo            VARCHAR(20) NOT NULL,
    unidade         VARCHAR(20) NOT NULL
);

CREATE TABLE medicao (
    id              BIGSERIAL PRIMARY KEY,
    dispositivo_id  BIGINT           NOT NULL REFERENCES dispositivo (id) ON DELETE CASCADE,
    tensao          DOUBLE PRECISION NOT NULL,
    corrente        DOUBLE PRECISION NOT NULL,
    potencia        DOUBLE PRECISION NOT NULL,
    energia_kwh     DOUBLE PRECISION NOT NULL,
    custo           NUMERIC(12, 4)   NOT NULL,
    instante        TIMESTAMPTZ      NOT NULL,
    origem          VARCHAR(20)      NOT NULL
);

CREATE INDEX idx_medicao_dispositivo_instante ON medicao (dispositivo_id, instante DESC);

CREATE TABLE tarifa (
    id               BIGSERIAL PRIMARY KEY,
    residencia_id    BIGINT         NOT NULL REFERENCES residencia (id) ON DELETE CASCADE,
    valor_kwh        NUMERIC(10, 4) NOT NULL,
    vigencia_inicio  DATE           NOT NULL,
    vigencia_fim     DATE
);

CREATE TABLE alerta (
    id              BIGSERIAL PRIMARY KEY,
    dispositivo_id  BIGINT           NOT NULL REFERENCES dispositivo (id) ON DELETE CASCADE,
    grandeza        VARCHAR(20)      NOT NULL,
    limiar          DOUBLE PRECISION NOT NULL,
    mensagem        VARCHAR(255)     NOT NULL,
    ativo           BOOLEAN          NOT NULL,
    disparado       BOOLEAN          NOT NULL
);

CREATE TABLE meta_consumo (
    id             BIGSERIAL PRIMARY KEY,
    residencia_id  BIGINT           NOT NULL REFERENCES residencia (id) ON DELETE CASCADE,
    limite_kwh     DOUBLE PRECISION NOT NULL,
    inicio         DATE             NOT NULL,
    fim            DATE             NOT NULL
);

CREATE TABLE relatorio (
    id             BIGSERIAL PRIMARY KEY,
    residencia_id  BIGINT           NOT NULL REFERENCES residencia (id) ON DELETE CASCADE,
    inicio         DATE             NOT NULL,
    fim            DATE             NOT NULL,
    consumo_kwh    DOUBLE PRECISION NOT NULL,
    custo_total    NUMERIC(12, 4)   NOT NULL,
    previsao_kwh   DOUBLE PRECISION NOT NULL,
    criado_em      TIMESTAMPTZ      NOT NULL
);
