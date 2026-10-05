package br.com.agilizadelivery.entity.model.enums;

/**
 * Causa da pendencia de revisao de uma entrega.
 *
 * SEM_COORDENADA         - endereco nao geocodificado (UC01 A1)
 * SEM_DISTANCIA          - API de rotas indisponivel (DVP 1.5.5, passo 1)
 * CONTESTACAO_ENTREGADOR - o entregador discordou do valor apurado e
 *                          pediu revisao pelo aplicativo (UC08)
 *
 * Distancia acima da ultima faixa nao gera pendencia: aplica-se o valor
 * de fora de area configurado na loja (DVP 1.5.5, passo 3).
 */
public enum MotivoRevisao {

    SEM_COORDENADA,
    SEM_DISTANCIA,
    CONTESTACAO_ENTREGADOR
}
