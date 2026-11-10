package br.com.agilizadelivery.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.dto.DtoMapper;
import br.com.agilizadelivery.dto.request.CriarUsuarioRequest;
import br.com.agilizadelivery.dto.response.UsuarioResponse;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.repository.UsuarioRepository;
import br.com.agilizadelivery.service.UsuarioService;
import br.com.agilizadelivery.service.support.BuscadorTenant;
import br.com.agilizadelivery.service.support.CadastroUsuario;

/**
 * Usuarios de uma loja: administradores do restaurante e operadores.
 *
 * ADMIN_GERAL e global e nao pertence a loja; ENTREGADOR nasce junto com
 * o cadastro do motoboy (HU09), pelo MotoboyService.
 */
@Service
@Transactional(readOnly = true)
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository repository;
    private final BuscadorTenant buscador;
    private final CadastroUsuario cadastroUsuario;

    public UsuarioServiceImpl(
            UsuarioRepository repository,
            BuscadorTenant buscador,
            CadastroUsuario cadastroUsuario) {
        this.repository = repository;
        this.buscador = buscador;
        this.cadastroUsuario = cadastroUsuario;
    }

    @Override
    @Transactional
    public UsuarioResponse criar(UUID estabelecimentoId, CriarUsuarioRequest request) {
        if (request.perfil() == PerfilUsuario.ADMIN_GERAL || request.perfil() == PerfilUsuario.ENTREGADOR) {
            throw new RegraDeNegocioException(
                    "O perfil informado não pode ser criado por esta operação: "
                            + "entregadores são cadastrados pela gestão da frota.");
        }
        Usuario usuario = cadastroUsuario.criar(
                buscador.estabelecimentoAtivo(estabelecimentoId),
                request.nome(),
                request.loginEmail(),
                request.senha(),
                request.perfil());
        return DtoMapper.toResponse(usuario);
    }

    @Override
    public UsuarioResponse buscarPorId(UUID estabelecimentoId, UUID usuarioId) {
        return DtoMapper.toResponse(buscador.usuario(estabelecimentoId, usuarioId));
    }

    @Override
    public List<UsuarioResponse> listarPorEstabelecimento(UUID estabelecimentoId) {
        return repository.findByEstabelecimentoId(estabelecimentoId).stream()
                .map(DtoMapper::toResponse)
                .toList();
    }

    @Override
    public List<UsuarioResponse> listarPorEstabelecimentoEPerfil(UUID estabelecimentoId, PerfilUsuario perfil) {
        return repository.findByEstabelecimentoIdAndPerfil(estabelecimentoId, perfil).stream()
                .map(DtoMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public UsuarioResponse alterarAtivo(UUID estabelecimentoId, UUID usuarioId, boolean ativo) {
        Usuario usuario = buscador.usuario(estabelecimentoId, usuarioId);
        usuario.setAtivo(ativo);
        return DtoMapper.toResponse(repository.save(usuario));
    }
}
