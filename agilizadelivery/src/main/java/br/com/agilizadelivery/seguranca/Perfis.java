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
 *
 * Um token so vale no proprio canal (UC07 E4): os tres primeiros exigem
 * BACKOFFICE aqui; o APP e exigido em EntregadorController.estabelecimento.
 */
public final class Perfis {

    private static final String NO_BACKOFFICE = " and principal.canal().name() == 'BACKOFFICE'";

    public static final String OPERACAO = "hasAnyRole('OPERADOR', 'ADMIN_RESTAURANTE')" + NO_BACKOFFICE;
    public static final String LOJA = "hasRole('ADMIN_RESTAURANTE')" + NO_BACKOFFICE;
    public static final String PLATAFORMA = "hasRole('ADMIN_GERAL')" + NO_BACKOFFICE;
    public static final String ENTREGADOR = "hasRole('ENTREGADOR')";

    private Perfis() {
    }
}
