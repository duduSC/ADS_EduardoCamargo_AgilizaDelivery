package br.com.agilizadelivery.seguranca;

/**
 * Expressoes de autorizacao por perfil, para @PreAuthorize nos controllers.
 *
 * Regra de acesso (D004, DVP 3.1, nota da tabela de casos de uso):
 * - OPERACAO: o que o OPERADOR faz (despacho, monitoramento, acerto,
 *   caixa). ADMIN_RESTAURANTE tambem acessa.
 * - LOJA: operacoes de loja (entregadores, faixas, parametros,
 *   integracao, reabrir caixa). So ADMIN_RESTAURANTE.
 * - PLATAFORMA: restaurantes parceiros. So ADMIN_GERAL.
 * - ENTREGADOR: operacoes do aplicativo.
 */
public final class Perfis {

    public static final String OPERACAO = "hasAnyRole('OPERADOR', 'ADMIN_RESTAURANTE')";
    public static final String LOJA = "hasRole('ADMIN_RESTAURANTE')";
    public static final String PLATAFORMA = "hasRole('ADMIN_GERAL')";
    public static final String ENTREGADOR = "hasRole('ENTREGADOR')";

    private Perfis() {
    }
}
