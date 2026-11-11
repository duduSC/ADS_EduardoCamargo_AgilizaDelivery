-- =====================================================================
-- Agiliza Delivery - schema inicial (DVP 3.1, secao 1.5.4)
--
-- Convencoes do dicionario de dados:
--   * datas de controle criado_em (NOT NULL) / atualizado_em (NULL) em toda
--     tabela com PK UUID; tabelas de registro (append-only) usam BIGSERIAL
--     e so carregam o proprio carimbo de tempo;
--   * campos de dominio fechado sao ENUM nativo do PostgreSQL. O nome de
--     cada tipo e o nome simples da enum Java (o Hibernate usa esse nome em
--     @JdbcTypeCode(SqlTypes.NAMED_ENUM)); o PostgreSQL o guarda em
--     minusculas (ex.: StatusEntrega -> statusentrega);
--   * coordenadas em DECIMAL(10,7).
--   * provedor e VARCHAR(20) nas duas tabelas (DVP 3.0); os valores aceitos
--     sao validados pela aplicacao.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------
-- Tipos enumerados (DVP 1.5.4 e 1.4.4)
-- ---------------------------------------------------------------------
CREATE TYPE StatusEstabelecimento        AS ENUM ('ATIVO', 'INATIVO');
CREATE TYPE PerfilUsuario                AS ENUM ('ADMIN_GERAL', 'ADMIN_RESTAURANTE', 'OPERADOR', 'ENTREGADOR');
CREATE TYPE StatusCadastroMotoboy        AS ENUM ('ATIVO', 'BLOQUEADO');
CREATE TYPE StatusDisponibilidadeMotoboy AS ENUM ('OFFLINE', 'ONLINE', 'EM_ROTA');
CREATE TYPE EtapaFalhaIntegracao         AS ENUM ('AUTENTICACAO', 'CONSULTA', 'CONFIRMACAO', 'GEOCODIFICACAO', 'PERSISTENCIA');
CREATE TYPE StatusFalhaIntegracao        AS ENUM ('PENDENTE', 'REPROCESSADA', 'DESCARTADA');
CREATE TYPE FormaPagamento               AS ENUM ('DINHEIRO', 'CREDITO', 'DEBITO', 'PIX', 'VALE');
CREATE TYPE StatusEntrega                AS ENUM ('AGUARDANDO_DESPACHO', 'DESPACHADA', 'EM_ROTA', 'ENTREGUE', 'FALHA', 'CANCELADA');
CREATE TYPE MotivoRevisao                AS ENUM ('SEM_COORDENADA', 'SEM_DISTANCIA', 'CONTESTACAO_ENTREGADOR');
CREATE TYPE StatusLote                   AS ENUM ('DESPACHADO', 'EM_ROTA', 'CONCLUIDO', 'CANCELADO');
CREATE TYPE StatusRecibo                 AS ENUM ('GERADO', 'PAGO', 'CANCELADO');
CREATE TYPE StatusCaixa                  AS ENUM ('ABERTO', 'FECHADO', 'REABERTO');

-- ---------------------------------------------------------------------
-- ESTABELECIMENTO - raiz da multilocacao
-- ---------------------------------------------------------------------
CREATE TABLE estabelecimento (
    id                          UUID                  NOT NULL,
    razao_social                VARCHAR(150)          NOT NULL,
    nome_fantasia               VARCHAR(100)          NOT NULL,
    cnpj                        VARCHAR(14)           NOT NULL,
    telefone                    VARCHAR(15),
    endereco_completo           VARCHAR(255)          NOT NULL,
    latitude                    DECIMAL(10,7)         NOT NULL,
    longitude                   DECIMAL(10,7)         NOT NULL,
    status                      StatusEstabelecimento NOT NULL,
    max_pedidos_por_lote        SMALLINT              NOT NULL DEFAULT 5,
    valor_repasse_fora_de_area  DECIMAL(10,2),
    criado_em                   TIMESTAMP             NOT NULL,
    atualizado_em               TIMESTAMP,
    CONSTRAINT pk_estabelecimento PRIMARY KEY (id),
    CONSTRAINT uk_estabelecimento_cnpj UNIQUE (cnpj)
);

-- ---------------------------------------------------------------------
-- USUARIO - credencial de qualquer perfil
-- ---------------------------------------------------------------------
CREATE TABLE usuario (
    id                  UUID          NOT NULL,
    estabelecimento_id  UUID,
    nome                VARCHAR(120)  NOT NULL,
    login_email         VARCHAR(150)  NOT NULL,
    senha_hash          VARCHAR(60)   NOT NULL,
    perfil              PerfilUsuario NOT NULL,
    ativo               BOOLEAN       NOT NULL DEFAULT TRUE,
    ultimo_acesso_em    TIMESTAMP,
    criado_em           TIMESTAMP     NOT NULL,
    atualizado_em       TIMESTAMP,
    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT uk_usuario_login_email UNIQUE (login_email),
    CONSTRAINT fk_usuario_estabelecimento FOREIGN KEY (estabelecimento_id) REFERENCES estabelecimento (id)
);

-- ---------------------------------------------------------------------
-- MOTOBOY - dados operacionais do entregador
-- ---------------------------------------------------------------------
CREATE TABLE motoboy (
    id                      UUID                         NOT NULL,
    usuario_id              UUID                         NOT NULL,
    cpf_criptografado       VARCHAR(255)                 NOT NULL,
    cpf_hash                VARCHAR(64)                  NOT NULL,
    telefone                VARCHAR(15)                  NOT NULL,
    placa_veiculo           VARCHAR(8),
    status_cadastro         StatusCadastroMotoboy        NOT NULL,
    status_disponibilidade  StatusDisponibilidadeMotoboy NOT NULL,
    ultima_latitude         DECIMAL(10,7),
    ultima_longitude        DECIMAL(10,7),
    ultima_posicao_em       TIMESTAMP,
    criado_em               TIMESTAMP                    NOT NULL,
    atualizado_em           TIMESTAMP,
    CONSTRAINT pk_motoboy PRIMARY KEY (id),
    CONSTRAINT uk_motoboy_usuario UNIQUE (usuario_id),
    CONSTRAINT uk_motoboy_cpf_hash UNIQUE (cpf_hash),
    CONSTRAINT fk_motoboy_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);

-- ---------------------------------------------------------------------
-- CONFIGURACAO_INTEGRACAO - vinculo da loja com o provedor
-- ---------------------------------------------------------------------
CREATE TABLE configuracao_integracao (
    id                  UUID         NOT NULL,
    estabelecimento_id  UUID         NOT NULL,
    provedor            VARCHAR(20)  NOT NULL,
    merchant_id         VARCHAR(100) NOT NULL,
    ativo               BOOLEAN      NOT NULL,
    ultimo_polling_em   TIMESTAMP,
    criado_em           TIMESTAMP    NOT NULL,
    atualizado_em       TIMESTAMP,
    CONSTRAINT pk_configuracao_integracao PRIMARY KEY (id),
    CONSTRAINT uk_configuracao_estabelecimento_provedor UNIQUE (estabelecimento_id, provedor),
    CONSTRAINT uk_configuracao_provedor_merchant UNIQUE (provedor, merchant_id),
    CONSTRAINT fk_configuracao_estabelecimento FOREIGN KEY (estabelecimento_id) REFERENCES estabelecimento (id)
);

-- ---------------------------------------------------------------------
-- FALHA_INTEGRACAO - fila de erro (RNF04), append-only
-- ---------------------------------------------------------------------
CREATE TABLE falha_integracao (
    id                          BIGSERIAL             NOT NULL,
    configuracao_integracao_id  UUID                  NOT NULL,
    id_externo                  VARCHAR(100),
    etapa                       EtapaFalhaIntegracao  NOT NULL,
    mensagem_erro               TEXT                  NOT NULL,
    tentativas                  SMALLINT              NOT NULL,
    payload_json                JSONB,
    status                      StatusFalhaIntegracao NOT NULL,
    ocorrido_em                 TIMESTAMP             NOT NULL,
    resolvido_em                TIMESTAMP,
    CONSTRAINT pk_falha_integracao PRIMARY KEY (id),
    CONSTRAINT fk_falha_configuracao FOREIGN KEY (configuracao_integracao_id) REFERENCES configuracao_integracao (id)
);

-- ---------------------------------------------------------------------
-- PEDIDO_EXTERNO - copia fiel do pedido do provedor (RNF13)
-- ---------------------------------------------------------------------
CREATE TABLE pedido_externo (
    id                  UUID           NOT NULL,
    estabelecimento_id  UUID           NOT NULL,
    provedor            VARCHAR(20)    NOT NULL,
    id_externo          VARCHAR(100)   NOT NULL,
    numero_exibicao     VARCHAR(20),
    status_externo      VARCHAR(30),
    nome_cliente        VARCHAR(120)   NOT NULL,
    telefone_cliente    VARCHAR(20),
    valor_itens         DECIMAL(10,2),
    valor_taxa_entrega  DECIMAL(10,2),
    valor_total         DECIMAL(10,2)  NOT NULL,
    forma_pagamento     FormaPagamento,
    pago_online         BOOLEAN        NOT NULL,
    troco_para          DECIMAL(10,2),
    observacoes         TEXT,
    payload_json        JSONB          NOT NULL,
    processado_em       TIMESTAMP,
    criado_em           TIMESTAMP      NOT NULL,
    atualizado_em       TIMESTAMP,
    CONSTRAINT pk_pedido_externo PRIMARY KEY (id),
    CONSTRAINT uk_pedido_externo_provedor_id_externo UNIQUE (provedor, id_externo),
    CONSTRAINT fk_pedido_externo_estabelecimento FOREIGN KEY (estabelecimento_id) REFERENCES estabelecimento (id)
);

-- ---------------------------------------------------------------------
-- ITEM_PEDIDO - itens do pedido importado, append-only
-- ---------------------------------------------------------------------
CREATE TABLE item_pedido (
    id                 BIGSERIAL     NOT NULL,
    pedido_externo_id  UUID          NOT NULL,
    descricao          VARCHAR(200)  NOT NULL,
    quantidade         SMALLINT      NOT NULL,
    valor_unitario     DECIMAL(10,2),
    observacao         VARCHAR(255),
    CONSTRAINT pk_item_pedido PRIMARY KEY (id),
    CONSTRAINT ck_item_pedido_quantidade CHECK (quantidade > 0),
    CONSTRAINT fk_item_pedido_pedido_externo FOREIGN KEY (pedido_externo_id) REFERENCES pedido_externo (id)
);

-- ---------------------------------------------------------------------
-- DISTANCIA_PRECO - faixas de preco por distancia
-- ---------------------------------------------------------------------
CREATE TABLE distancia_preco (
    id                  UUID          NOT NULL,
    estabelecimento_id  UUID          NOT NULL,
    de_km               DECIMAL(5,2)  NOT NULL,
    ate_km              DECIMAL(5,2)  NOT NULL,
    valor_pago          DECIMAL(10,2) NOT NULL,
    ativo               BOOLEAN       NOT NULL,
    vigente_desde       DATE          NOT NULL,
    criado_em           TIMESTAMP     NOT NULL,
    atualizado_em       TIMESTAMP,
    CONSTRAINT pk_distancia_preco PRIMARY KEY (id),
    CONSTRAINT ck_distancia_preco_intervalo CHECK (ate_km > de_km),
    CONSTRAINT ck_distancia_preco_valor_pago CHECK (valor_pago > 0),
    CONSTRAINT fk_distancia_preco_estabelecimento FOREIGN KEY (estabelecimento_id) REFERENCES estabelecimento (id),
    -- Regra de integridade (1) do DVP: sem sobreposicao entre as faixas ativas
    -- de uma mesma loja, no intervalo [de_km, ate_km). A regra (2), sem
    -- lacunas, fica na camada de servico.
    CONSTRAINT ex_distancia_preco_sobreposicao EXCLUDE USING gist (
        estabelecimento_id WITH =,
        numrange(de_km, ate_km, '[)') WITH &&
    ) WHERE (ativo)
);

-- ---------------------------------------------------------------------
-- CAIXA - periodo de operacao da loja
-- ---------------------------------------------------------------------
CREATE TABLE caixa (
    id                     UUID          NOT NULL,
    estabelecimento_id     UUID          NOT NULL,
    data_referencia        DATE          NOT NULL,
    aberto_em              TIMESTAMP     NOT NULL,
    fechado_em             TIMESTAMP,
    usuario_fechamento_id  UUID,
    status                 StatusCaixa   NOT NULL,
    qtd_recibos            SMALLINT,
    valor_total            DECIMAL(12,2),
    reaberto_em            TIMESTAMP,
    usuario_reabertura_id  UUID,
    motivo_reabertura      VARCHAR(255),
    criado_em              TIMESTAMP     NOT NULL,
    atualizado_em          TIMESTAMP,
    CONSTRAINT pk_caixa PRIMARY KEY (id),
    CONSTRAINT uk_caixa_estabelecimento_data UNIQUE (estabelecimento_id, data_referencia),
    CONSTRAINT fk_caixa_estabelecimento FOREIGN KEY (estabelecimento_id) REFERENCES estabelecimento (id),
    CONSTRAINT fk_caixa_usuario_fechamento FOREIGN KEY (usuario_fechamento_id) REFERENCES usuario (id),
    CONSTRAINT fk_caixa_usuario_reabertura FOREIGN KEY (usuario_reabertura_id) REFERENCES usuario (id)
);

-- ---------------------------------------------------------------------
-- LOTE_ENTREGA - agrupamento despachado a um entregador
-- ---------------------------------------------------------------------
CREATE TABLE lote_entrega (
    id                      UUID          NOT NULL,
    estabelecimento_id      UUID          NOT NULL,
    motoboy_id              UUID          NOT NULL,
    usuario_despachante_id  UUID          NOT NULL,
    status                  StatusLote    NOT NULL,
    qtd_entregas            SMALLINT      NOT NULL,
    valor_total_repasse     DECIMAL(10,2),
    despachado_em           TIMESTAMP,
    concluido_em            TIMESTAMP,
    criado_em               TIMESTAMP     NOT NULL,
    atualizado_em           TIMESTAMP,
    CONSTRAINT pk_lote_entrega PRIMARY KEY (id),
    CONSTRAINT ck_lote_entrega_qtd_entregas CHECK (qtd_entregas > 0),
    CONSTRAINT fk_lote_estabelecimento FOREIGN KEY (estabelecimento_id) REFERENCES estabelecimento (id),
    CONSTRAINT fk_lote_motoboy FOREIGN KEY (motoboy_id) REFERENCES motoboy (id),
    CONSTRAINT fk_lote_usuario_despachante FOREIGN KEY (usuario_despachante_id) REFERENCES usuario (id)
);

-- Limite qtd_entregas <= ESTABELECIMENTO.max_pedidos_por_lote (HU02).
-- Validado na camada de servico e reforcado aqui, pois um CHECK nao pode
-- consultar outra tabela. So verifica quando o lote nasce ou quando a
-- quantidade muda: uma reducao posterior do limite da loja nao impede a
-- atualizacao de status de lotes ja despachados.
CREATE FUNCTION fn_lote_entrega_limite() RETURNS TRIGGER AS $$
DECLARE
    limite SMALLINT;
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.qtd_entregas IS NOT DISTINCT FROM OLD.qtd_entregas THEN
        RETURN NEW;
    END IF;

    SELECT max_pedidos_por_lote INTO limite
      FROM estabelecimento
     WHERE id = NEW.estabelecimento_id;

    IF limite IS NOT NULL AND NEW.qtd_entregas > limite THEN
        RAISE EXCEPTION 'lote_entrega: qtd_entregas (%) excede max_pedidos_por_lote (%) do estabelecimento %',
            NEW.qtd_entregas, limite, NEW.estabelecimento_id
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_lote_entrega_limite
    BEFORE INSERT OR UPDATE ON lote_entrega
    FOR EACH ROW EXECUTE FUNCTION fn_lote_entrega_limite();

-- ---------------------------------------------------------------------
-- RECIBO - comprovante do acerto financeiro
-- ---------------------------------------------------------------------
CREATE TABLE recibo (
    id                  UUID          NOT NULL,
    estabelecimento_id  UUID          NOT NULL,
    caixa_id            UUID          NOT NULL,
    motoboy_id          UUID          NOT NULL,
    usuario_gerador_id  UUID          NOT NULL,
    data_referencia     DATE          NOT NULL,
    periodo_inicio      TIMESTAMP     NOT NULL,
    periodo_fim         TIMESTAMP     NOT NULL,
    qtd_entregas        SMALLINT      NOT NULL,
    valor_total         DECIMAL(10,2) NOT NULL,
    status              StatusRecibo  NOT NULL,
    pago_em             TIMESTAMP,
    criado_em           TIMESTAMP     NOT NULL,
    atualizado_em       TIMESTAMP,
    CONSTRAINT pk_recibo PRIMARY KEY (id),
    CONSTRAINT fk_recibo_estabelecimento FOREIGN KEY (estabelecimento_id) REFERENCES estabelecimento (id),
    CONSTRAINT fk_recibo_caixa FOREIGN KEY (caixa_id) REFERENCES caixa (id),
    CONSTRAINT fk_recibo_motoboy FOREIGN KEY (motoboy_id) REFERENCES motoboy (id),
    CONSTRAINT fk_recibo_usuario_gerador FOREIGN KEY (usuario_gerador_id) REFERENCES usuario (id)
);

-- ---------------------------------------------------------------------
-- ENTREGA - unidade logistica e de cobranca do repasse
-- ---------------------------------------------------------------------
CREATE TABLE entrega (
    id                       UUID           NOT NULL,
    estabelecimento_id       UUID           NOT NULL,
    pedido_externo_id        UUID,
    lote_id                  UUID,
    ordem_rota               SMALLINT,
    faixa_preco_id           UUID,
    recibo_id                UUID,
    nome_cliente             VARCHAR(120)   NOT NULL,
    telefone_cliente         VARCHAR(20),
    endereco_completo        VARCHAR(255)   NOT NULL,
    bairro                   VARCHAR(100),
    cep                      VARCHAR(9),
    complemento              VARCHAR(100),
    ponto_referencia         VARCHAR(150),
    latitude                 DECIMAL(10,7),
    longitude                DECIMAL(10,7),
    valor_pedido             DECIMAL(10,2),
    forma_pagamento          FormaPagamento,
    pago_online              BOOLEAN        NOT NULL DEFAULT FALSE,
    troco_para               DECIMAL(10,2),
    observacoes              TEXT,
    distancia_km             DECIMAL(6,2),
    valor_repasse            DECIMAL(10,2),
    status                   StatusEntrega  NOT NULL,
    motivo_falha             VARCHAR(100),
    pendente_revisao         BOOLEAN        NOT NULL DEFAULT FALSE,
    motivo_revisao           MotivoRevisao,
    pronto_para_despacho_em  TIMESTAMP,
    despachada_em            TIMESTAMP,
    finalizada_em            TIMESTAMP,
    criado_em                TIMESTAMP      NOT NULL,
    atualizado_em            TIMESTAMP,
    CONSTRAINT pk_entrega PRIMARY KEY (id),
    CONSTRAINT ck_entrega_ordem_rota CHECK (ordem_rota > 0),
    CONSTRAINT fk_entrega_estabelecimento FOREIGN KEY (estabelecimento_id) REFERENCES estabelecimento (id),
    CONSTRAINT fk_entrega_pedido_externo FOREIGN KEY (pedido_externo_id) REFERENCES pedido_externo (id),
    CONSTRAINT fk_entrega_lote FOREIGN KEY (lote_id) REFERENCES lote_entrega (id),
    CONSTRAINT fk_entrega_faixa_preco FOREIGN KEY (faixa_preco_id) REFERENCES distancia_preco (id),
    CONSTRAINT fk_entrega_recibo FOREIGN KEY (recibo_id) REFERENCES recibo (id)
);

-- ---------------------------------------------------------------------
-- ENTREGA_STATUS_HISTORICO - trilha de cada transicao, append-only
-- ---------------------------------------------------------------------
CREATE TABLE entrega_status_historico (
    id                         BIGSERIAL     NOT NULL,
    entrega_id                 UUID          NOT NULL,
    status_anterior            StatusEntrega,
    status_novo                StatusEntrega NOT NULL,
    registrado_por_usuario_id  UUID,
    latitude                   DECIMAL(10,7),
    longitude                  DECIMAL(10,7),
    registrado_em              TIMESTAMP     NOT NULL,
    CONSTRAINT pk_entrega_status_historico PRIMARY KEY (id),
    CONSTRAINT fk_historico_entrega FOREIGN KEY (entrega_id) REFERENCES entrega (id),
    CONSTRAINT fk_historico_usuario FOREIGN KEY (registrado_por_usuario_id) REFERENCES usuario (id)
);

-- ---------------------------------------------------------------------
-- ENTREGA_AJUSTE_VALOR - trilha de ajustes manuais (UC03 A1), append-only
-- ---------------------------------------------------------------------
CREATE TABLE entrega_ajuste_valor (
    id              BIGSERIAL     NOT NULL,
    entrega_id      UUID          NOT NULL,
    usuario_id      UUID          NOT NULL,
    valor_anterior  DECIMAL(10,2) NOT NULL,
    valor_novo      DECIMAL(10,2) NOT NULL,
    motivo          VARCHAR(255)  NOT NULL,
    ajustado_em     TIMESTAMP     NOT NULL,
    CONSTRAINT pk_entrega_ajuste_valor PRIMARY KEY (id),
    CONSTRAINT ck_entrega_ajuste_valor_novo CHECK (valor_novo >= 0),
    CONSTRAINT fk_ajuste_entrega FOREIGN KEY (entrega_id) REFERENCES entrega (id),
    CONSTRAINT fk_ajuste_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id)
);

-- ---------------------------------------------------------------------
-- Indices de apoio as consultas operacionais filtradas por estabelecimento
-- (o PostgreSQL nao cria indice automatico para FK).
-- ---------------------------------------------------------------------
CREATE INDEX ix_usuario_estabelecimento        ON usuario (estabelecimento_id);
CREATE INDEX ix_entrega_estabelecimento_status ON entrega (estabelecimento_id, status);
CREATE INDEX ix_entrega_lote                   ON entrega (lote_id);
CREATE INDEX ix_entrega_recibo                 ON entrega (recibo_id);
CREATE INDEX ix_lote_estabelecimento_status    ON lote_entrega (estabelecimento_id, status);
CREATE INDEX ix_lote_motoboy                   ON lote_entrega (motoboy_id);
CREATE INDEX ix_recibo_caixa                   ON recibo (caixa_id);
CREATE INDEX ix_recibo_motoboy                 ON recibo (motoboy_id);
CREATE INDEX ix_historico_entrega              ON entrega_status_historico (entrega_id);
CREATE INDEX ix_ajuste_entrega                 ON entrega_ajuste_valor (entrega_id);
CREATE INDEX ix_item_pedido_pedido_externo     ON item_pedido (pedido_externo_id);
CREATE INDEX ix_falha_configuracao_status      ON falha_integracao (configuracao_integracao_id, status);
