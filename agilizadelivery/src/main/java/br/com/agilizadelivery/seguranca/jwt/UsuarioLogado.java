package br.com.agilizadelivery.seguranca.jwt;

import java.util.UUID;

import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.exception.AcessoNegadoException;
import br.com.agilizadelivery.service.CanalAcesso;

/**
 * Identidade extraida de um token de acesso valido. E o unico lugar de
 * onde os controllers tiram o estabelecimento (Constituicao 5.6): nenhum
 * id de loja vindo do corpo ou da URL vale como fonte de verdade.
 *
 * estabelecimentoId e nulo apenas para ADMIN_GERAL.
 */
public record UsuarioLogado(UUID usuarioId, UUID estabelecimentoId, PerfilUsuario perfil, CanalAcesso canal) {

    /** Estabelecimento do token; operacoes de loja nunca rodam sem ele. */
    public UUID estabelecimentoObrigatorio() {
        if (estabelecimentoId == null) {
            throw new AcessoNegadoException("Esta operação exige um usuário vinculado a um estabelecimento.");
        }
        return estabelecimentoId;
    }
}
