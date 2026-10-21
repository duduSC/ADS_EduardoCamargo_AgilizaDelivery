package br.com.agilizadelivery.exception.handler;

/**
 * Catalogo padronizado de codigos de erro da aplicacao.
 *
 * Utilizado pelos clientes da API (Web React e Mobile Flutter) para
 * identificar a causa exata do erro de forma desacoplada de mensagens
 * em texto, permitindo internacionalizacao e tomada de decisao nas interfaces.
 */
public enum CodigoErro {

    // Erros genericos e de infraestrutura
    ERRO_INTERNO_SERVIDOR,
    DADOS_INVALIDOS,
    CORPO_REQUISICAO_INVALIDO,
    PARAMETRO_INVALIDO,

    // 404 - Recursos nao encontrados
    RECURSO_NAO_ENCONTRADO,
    ESTABELECIMENTO_NAO_ENCONTRADO,
    USUARIO_NAO_ENCONTRADO,
    MOTOBOY_NAO_ENCONTRADO,
    ENTREGA_NAO_ENCONTRADA,
    LOTE_NAO_ENCONTRADO,
    CAIXA_NAO_ENCONTRADO,
    RECIBO_NAO_ENCONTRADO,
    FAIXA_PRECO_NAO_ENCONTRADA,

    // 422 / 400 - Regras de negocio e maquina de estados
    REGRA_NEGOCIO_VIOLADA,
    TRANSICAO_ESTADO_INVALIDA,
    CAIXA_FECHADO,
    LIMITE_LOTE_EXCEDIDO,
    MOTOBOY_INDISPONIVEL,
    ENTREGA_INAPTA_LOTE,
    ENTREGA_INAPTA_RECIBO,
    RECIBO_INALTERAVEL,
    FAIXA_PRECO_INVALIDA,
    AJUSTE_VALOR_NAO_PERMITIDO,

    // 409 - Conflitos de unicidade / Idempotencia
    DADO_DUPLICADO,
    CNPJ_JA_CADASTRADO,
    CPF_JA_CADASTRADO,
    EMAIL_JA_CADASTRADO,
    CAIXA_JA_ABERTO,
    PEDIDO_JA_IMPORTADO,

    // 403 / 401 - Seguranca e Multilocacao
    ACESSO_NEGADO,
    ESTABELECIMENTO_INATIVO,
    USUARIO_BLOQUEADO,
    VIOLACAO_MULTITENANT,

    // 502 / 503 - Integracoes externas
    INTEGRACAO_EXTERNA_ERRO
}
