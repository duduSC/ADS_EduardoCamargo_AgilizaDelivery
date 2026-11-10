package br.com.agilizadelivery.service.support;

import java.util.Arrays;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import br.com.agilizadelivery.exception.AcessoNegadoException;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.exception.EstabelecimentoInativoException;
import br.com.agilizadelivery.repository.EstabelecimentoRepository;
import br.com.agilizadelivery.repository.MotoboyRepository;
import br.com.agilizadelivery.repository.UsuarioRepository;

/**
 * Ponto unico de busca das entidades compartilhadas entre os servicos,
 * sempre filtradas pelo estabelecimento (HU13).
 *
 * Um recurso de outra loja e tratado como inexistente, para nao revelar
 * que o identificador existe em outro estabelecimento.
 */
@Component
public class BuscadorTenant {

    private final EstabelecimentoRepository estabelecimentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MotoboyRepository motoboyRepository;

    public BuscadorTenant(
            EstabelecimentoRepository estabelecimentoRepository,
            UsuarioRepository usuarioRepository,
            MotoboyRepository motoboyRepository) {
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.motoboyRepository = motoboyRepository;
    }

    public Estabelecimento estabelecimento(UUID estabelecimentoId) {
        return estabelecimentoRepository.findById(estabelecimentoId)
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(Estabelecimento.class, estabelecimentoId));
    }

    /** Loja INATIVO nao opera (HU12). */
    public Estabelecimento estabelecimentoAtivo(UUID estabelecimentoId) {
        Estabelecimento estabelecimento = estabelecimento(estabelecimentoId);
        if (!StatusEstabelecimento.ATIVO.equals(estabelecimento.getStatus())) {
            throw new EstabelecimentoInativoException(estabelecimento.getNomeFantasia());
        }
        return estabelecimento;
    }

    public Usuario usuario(UUID estabelecimentoId, UUID usuarioId) {
        return usuarioRepository.findByIdAndEstabelecimentoId(usuarioId, estabelecimentoId)
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(Usuario.class, usuarioId));
    }

    /** Usuario da loja, ativo e com um dos perfis autorizados para a operacao. */
    public Usuario usuarioComPerfil(UUID estabelecimentoId, UUID usuarioId, PerfilUsuario... perfis) {
        Usuario usuario = usuario(estabelecimentoId, usuarioId);
        if (!Boolean.TRUE.equals(usuario.getAtivo())
                || Arrays.stream(perfis).noneMatch(perfil -> perfil.equals(usuario.getPerfil()))) {
            throw new AcessoNegadoException("O usuário não tem permissão para realizar esta operação.");
        }
        return usuario;
    }

    public Motoboy motoboy(UUID estabelecimentoId, UUID motoboyId) {
        return motoboyRepository.findByIdAndUsuarioEstabelecimentoId(motoboyId, estabelecimentoId)
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(Motoboy.class, motoboyId));
    }
}
