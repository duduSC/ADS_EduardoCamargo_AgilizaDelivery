package br.com.agilizadelivery.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.dto.DtoMapper;
import br.com.agilizadelivery.dto.request.AlterarDisponibilidadeMotoboyRequest;
import br.com.agilizadelivery.dto.request.CriarMotoboyRequest;
import br.com.agilizadelivery.dto.request.EditarMotoboyRequest;
import br.com.agilizadelivery.dto.response.MotoboyResponse;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCadastroMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import br.com.agilizadelivery.exception.CpfJaCadastradoException;
import br.com.agilizadelivery.exception.EstabelecimentoInativoException;
import br.com.agilizadelivery.exception.MotoboyIndisponivelException;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.exception.TransicaoEstadoInvalidaException;
import br.com.agilizadelivery.repository.MotoboyRepository;
import br.com.agilizadelivery.service.MotoboyService;
import br.com.agilizadelivery.service.seguranca.ProtetorCpf;
import br.com.agilizadelivery.service.support.BuscadorTenant;
import br.com.agilizadelivery.service.support.CadastroUsuario;
import br.com.agilizadelivery.service.support.Coordenadas;
import br.com.agilizadelivery.websocket.PublicadorTempoReal;

/**
 * Gestao da frota (UC05, HU09) e disponibilidade em campo (DVP 1.4.4).
 *
 * A disponibilidade EM_ROTA e controlada apenas pelo ciclo do lote; por
 * aqui o entregador alterna somente entre OFFLINE e ONLINE.
 */
@Service
@Transactional(readOnly = true)
public class MotoboyServiceImpl implements MotoboyService {

    private final MotoboyRepository repository;
    private final BuscadorTenant buscador;
    private final CadastroUsuario cadastroUsuario;
    private final ProtetorCpf protetorCpf;
    private final PublicadorTempoReal publicador;

    public MotoboyServiceImpl(
            MotoboyRepository repository,
            BuscadorTenant buscador,
            CadastroUsuario cadastroUsuario,
            ProtetorCpf protetorCpf,
            PublicadorTempoReal publicador) {
        this.repository = repository;
        this.buscador = buscador;
        this.cadastroUsuario = cadastroUsuario;
        this.protetorCpf = protetorCpf;
        this.publicador = publicador;
    }

    /** O cadastro gera o usuario ENTREGADOR vinculado a loja (HU09). */
    @Override
    @Transactional
    public MotoboyResponse criar(UUID estabelecimentoId, CriarMotoboyRequest request) {
        Estabelecimento estabelecimento = buscador.estabelecimentoAtivo(estabelecimentoId);
        String cpfHash = protetorCpf.hash(request.cpf());
        if (Boolean.TRUE.equals(repository.existsByCpfHash(cpfHash))) {
            throw new CpfJaCadastradoException();
        }

        Motoboy motoboy = new Motoboy();
        motoboy.setUsuario(cadastroUsuario.criar(
                estabelecimento, request.nome(), request.loginEmail(), request.senha(), PerfilUsuario.ENTREGADOR));
        motoboy.setCpfCriptografado(protetorCpf.cifrar(request.cpf()));
        motoboy.setCpfHash(cpfHash);
        motoboy.setTelefone(request.telefone());
        motoboy.setPlacaVeiculo(request.placaVeiculo());
        motoboy.setStatusCadastro(StatusCadastroMotoboy.ATIVO);
        motoboy.setStatusDisponibilidade(StatusDisponibilidadeMotoboy.OFFLINE);
        return DtoMapper.toResponse(repository.save(motoboy));
    }

    /** Edicao de dados operacionais pelo administrador (UC05, passo 2). */
    @Override
    @Transactional
    public MotoboyResponse editar(UUID estabelecimentoId, UUID motoboyId, EditarMotoboyRequest request) {
        Motoboy motoboy = buscador.motoboy(estabelecimentoId, motoboyId);
        if (request.telefone() != null) {
            motoboy.setTelefone(request.telefone());
        }
        if (request.placaVeiculo() != null) {
            motoboy.setPlacaVeiculo(request.placaVeiculo());
        }
        return DtoMapper.toResponse(repository.save(motoboy));
    }

    @Override
    public MotoboyResponse buscarPorId(UUID estabelecimentoId, UUID motoboyId) {
        return DtoMapper.toResponse(buscador.motoboy(estabelecimentoId, motoboyId));
    }

    @Override
    public List<MotoboyResponse> listarPorEstabelecimento(UUID estabelecimentoId) {
        return repository.findByUsuarioEstabelecimentoId(estabelecimentoId).stream()
                .map(DtoMapper::toResponse)
                .toList();
    }

    @Override
    public List<MotoboyResponse> listarPorStatusCadastro(UUID estabelecimentoId, StatusCadastroMotoboy status) {
        return repository.findByUsuarioEstabelecimentoIdAndStatusCadastro(estabelecimentoId, status).stream()
                .map(DtoMapper::toResponse)
                .toList();
    }

    @Override
    public List<MotoboyResponse> listarPorStatusDisponibilidade(
            UUID estabelecimentoId, StatusDisponibilidadeMotoboy status) {
        return repository.findByUsuarioEstabelecimentoIdAndStatusDisponibilidade(estabelecimentoId, status).stream()
                .map(DtoMapper::toResponse)
                .toList();
    }

    /** BLOQUEADO nao fica ONLINE nem recebe lote (HU09). */
    @Override
    @Transactional
    public MotoboyResponse alterarStatusCadastro(
            UUID estabelecimentoId,
            UUID motoboyId,
            StatusCadastroMotoboy status) {
        Motoboy motoboy = buscador.motoboy(estabelecimentoId, motoboyId);
        if (status == StatusCadastroMotoboy.BLOQUEADO) {
            if (motoboy.getStatusDisponibilidade() == StatusDisponibilidadeMotoboy.EM_ROTA) {
                throw new RegraDeNegocioException(
                        "O entregador está em rota; conclua ou devolva o lote antes de bloqueá-lo.");
            }
            motoboy.setStatusDisponibilidade(StatusDisponibilidadeMotoboy.OFFLINE);
        }
        motoboy.setStatusCadastro(status);
        return DtoMapper.toResponse(repository.save(motoboy));
    }

    @Override
    @Transactional
    public MotoboyResponse alterarDisponibilidade(
            UUID estabelecimentoId,
            UUID motoboyId,
            AlterarDisponibilidadeMotoboyRequest request) {
        Motoboy motoboy = buscador.motoboy(estabelecimentoId, motoboyId);
        StatusDisponibilidadeMotoboy atual = motoboy.getStatusDisponibilidade();
        StatusDisponibilidadeMotoboy destino = request.status();

        if (destino == StatusDisponibilidadeMotoboy.EM_ROTA || atual == StatusDisponibilidadeMotoboy.EM_ROTA) {
            throw new TransicaoEstadoInvalidaException(
                    "A disponibilidade EM_ROTA é controlada pelo lote: ela começa no despacho e termina na "
                            + "conclusão ou devolução do lote.");
        }
        if (destino == StatusDisponibilidadeMotoboy.ONLINE) {
            exigirAptoAFicarOnline(motoboy);
        }
        motoboy.setStatusDisponibilidade(destino);
        return DtoMapper.toResponse(repository.save(motoboy));
    }

    /** Telemetria so e aceita em ONLINE ou EM_ROTA (RNF07). */
    @Override
    @Transactional
    public MotoboyResponse atualizarPosicao(
            UUID estabelecimentoId,
            UUID motoboyId,
            BigDecimal latitude,
            BigDecimal longitude) {
        Motoboy motoboy = buscador.motoboy(estabelecimentoId, motoboyId);
        if (motoboy.getStatusDisponibilidade() == StatusDisponibilidadeMotoboy.OFFLINE) {
            throw new RegraDeNegocioException(
                    "A posição só é registrada enquanto o entregador estiver ONLINE ou EM_ROTA.");
        }
        motoboy.setUltimaPosicao(Coordenadas.validada(latitude, longitude));
        motoboy.setUltimaPosicaoEm(LocalDateTime.now());
        MotoboyResponse resposta = DtoMapper.toResponse(repository.save(motoboy));
        publicador.publicarFrota(estabelecimentoId, "POSICAO_ATUALIZADA", resposta);
        return resposta;
    }

    private void exigirAptoAFicarOnline(Motoboy motoboy) {
        String nome = motoboy.getUsuario().getNome();
        if (motoboy.getStatusCadastro() != StatusCadastroMotoboy.ATIVO) {
            throw new MotoboyIndisponivelException(nome, "o cadastro está bloqueado");
        }
        if (!Boolean.TRUE.equals(motoboy.getUsuario().getAtivo())) {
            throw new MotoboyIndisponivelException(nome, "o usuário está inativo");
        }
        Estabelecimento estabelecimento = motoboy.getUsuario().getEstabelecimento();
        if (estabelecimento.getStatus() != StatusEstabelecimento.ATIVO) {
            throw new EstabelecimentoInativoException(estabelecimento.getNomeFantasia());
        }
    }
}
