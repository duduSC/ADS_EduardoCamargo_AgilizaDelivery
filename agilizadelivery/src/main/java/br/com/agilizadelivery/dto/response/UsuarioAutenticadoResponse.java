package br.com.agilizadelivery.dto.response;

import java.util.UUID;

import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;

/**
 * Dados que alimentam o token: usuario, perfil e estabelecimento (UC07,
 * passo 5). estabelecimentoId e nulo para ADMIN_GERAL; motoboyId so e
 * preenchido para ENTREGADOR.
 */
public record UsuarioAutenticadoResponse(
        UUID usuarioId,
        UUID estabelecimentoId,
        UUID motoboyId,
        String nome,
        PerfilUsuario perfil) {
}
