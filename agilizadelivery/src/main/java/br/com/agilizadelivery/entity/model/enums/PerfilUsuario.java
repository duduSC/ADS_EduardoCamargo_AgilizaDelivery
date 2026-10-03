package br.com.agilizadelivery.entity.model.enums;

/**
 * Perfil de acesso, base da autorizacao (DVP 1.5.4 - USUARIO.perfil).
 * ADMIN_GERAL e global e nao pertence a nenhum estabelecimento.
 */
public enum PerfilUsuario {

    ADMIN_GERAL,
    ADMIN_RESTAURANTE,
    OPERADOR,
    ENTREGADOR
}
