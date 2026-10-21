package br.com.agilizadelivery.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;

public record UsuarioResponse(
        UUID id,
        UUID estabelecimentoId,
        String nome,
        String loginEmail,
        PerfilUsuario perfil,
        Boolean ativo,
        LocalDateTime ultimoAcessoEm,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {
}
