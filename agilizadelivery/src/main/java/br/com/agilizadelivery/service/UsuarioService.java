package br.com.agilizadelivery.service;

import java.util.List;
import java.util.UUID;

import br.com.agilizadelivery.dto.request.CriarUsuarioRequest;
import br.com.agilizadelivery.dto.response.UsuarioResponse;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;

public interface UsuarioService {

    UsuarioResponse criar(UUID estabelecimentoId, CriarUsuarioRequest request);

    UsuarioResponse buscarPorId(UUID estabelecimentoId, UUID usuarioId);

    List<UsuarioResponse> listarPorEstabelecimento(UUID estabelecimentoId);

    List<UsuarioResponse> listarPorEstabelecimentoEPerfil(UUID estabelecimentoId, PerfilUsuario perfil);

    UsuarioResponse alterarAtivo(UUID estabelecimentoId, UUID usuarioId, boolean ativo);
}
