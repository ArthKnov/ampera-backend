INSERT INTO usuario (nome, email, senha_hash)
VALUES ('Demo Ampéra', 'demo@ampera.local', '$2b$10$vbZhiWBswlxzTZDsV2Vq4.Ci0HFklD0CR5Aqs84AIdKJtx/Daya1e');

INSERT INTO residencia (usuario_id, nome, endereco)
VALUES (1, 'Casa', 'Rua do Laboratório, 100');

INSERT INTO comodo (residencia_id, nome)
VALUES (1, 'Sala');

INSERT INTO dispositivo (comodo_id, nome, codigo_mqtt)
VALUES (1, 'Medidor da sala', 'medidor-sala');

INSERT INTO sensor (dispositivo_id, tipo, unidade)
VALUES (1, 'TENSAO', 'V'),
       (1, 'CORRENTE', 'A');

INSERT INTO tarifa (residencia_id, valor_kwh, vigencia_inicio, vigencia_fim)
VALUES (1, 1.0000, DATE '2026-01-01', NULL);

INSERT INTO alerta (dispositivo_id, grandeza, limiar, mensagem, ativo, disparado)
VALUES (1, 'POTENCIA', 100, 'Potência acima de 100 W', TRUE, FALSE);

INSERT INTO meta_consumo (residencia_id, limite_kwh, inicio, fim)
VALUES (1, 100, DATE '2026-10-01', DATE '2026-10-31');
