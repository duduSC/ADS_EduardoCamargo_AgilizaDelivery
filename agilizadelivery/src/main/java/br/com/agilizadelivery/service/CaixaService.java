package br.com.agilizadelivery.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.StatusCaixa;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import br.com.agilizadelivery.exception.CaixaFechadoException;
import br.com.agilizadelivery.exception.CaixaJaAbertoException;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.exception.EstabelecimentoInativoException;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.exception.TransicaoEstadoInvalidaException;
import br.com.agilizadelivery.repository.CaixaRepository;
import br.com.agilizadelivery.repository.EstabelecimentoRepository;
import br.com.agilizadelivery.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servico responsavel pelo gerenciamento dos periodos de operacao (Caixa) de cada restaurante.
 *
 * Aplica os padroes de projeto Service Layer, Inversion of Control (IoC), Dependency Injection,
 * e tratamento consistente de excecoes de dominio.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CaixaService {

    private final CaixaRepository caixaRepository;
    private final EstabelecimentoRepository estabelecimentoRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * Abre o caixa do expediente para um restaurante em uma data de referencia.
     */
    @Transactional
    public Caixa abrirCaixa(UUID estabelecimentoId, LocalDate dataReferencia) {
        log.info("Tentativa de abertura de caixa para estabelecimento '{}' na data '{}'",
                estabelecimentoId, dataReferencia);

        Estabelecimento estabelecimento = estabelecimentoRepository.findById(estabelecimentoId)
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(Estabelecimento.class, estabelecimentoId));

        if (!StatusEstabelecimento.ATIVO.equals(estabelecimento.getStatus())) {
            throw new EstabelecimentoInativoException(estabelecimento.getNomeFantasia());
        }

        if (caixaRepository.existsByEstabelecimentoIdAndDataReferencia(estabelecimentoId, dataReferencia)) {
            throw new CaixaJaAbertoException(dataReferencia);
        }

        Caixa caixa = new Caixa();
        caixa.setEstabelecimento(estabelecimento);
        caixa.setDataReferencia(dataReferencia);
        caixa.setAbertoEm(LocalDateTime.now());
        caixa.setStatus(StatusCaixa.ABERTO);

        Caixa salvo = caixaRepository.save(caixa);
        log.info("Caixa '{}' aberto com sucesso para o estabelecimento '{}'", salvo.getId(), estabelecimentoId);
        return salvo;
    }

    /**
     * Busca um caixa pelo seu identificador unico UUID.
     */
    public Caixa buscarPorId(UUID caixaId) {
        return caixaRepository.findById(caixaId)
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(Caixa.class, caixaId));
    }

    /**
     * Busca o caixa de um estabelecimento para uma data especifica.
     */
    public Caixa buscarPorEstabelecimentoEData(UUID estabelecimentoId, LocalDate dataReferencia) {
        return caixaRepository.findByEstabelecimentoIdAndDataReferencia(estabelecimentoId, dataReferencia)
                .orElseThrow(() -> new EntidadeNaoEncontradaException(
                        String.format("Nenhum caixa encontrado para o estabelecimento '%s' na data %s.",
                                estabelecimentoId, dataReferencia)));
    }

    /**
     * Lista todos os caixas de um estabelecimento por status.
     */
    public List<Caixa> listarPorEstabelecimentoEStatus(UUID estabelecimentoId, StatusCaixa status) {
        return caixaRepository.findByEstabelecimentoIdAndStatus(estabelecimentoId, status);
    }

    /**
     * Fecha o expediente do caixa, tornando os recibos e entregas consolidados imutaveis.
     */
    @Transactional
    public Caixa fecharCaixa(UUID caixaId, UUID usuarioFechamentoId) {
        log.info("Fechando caixa '{}' pelo usuário '{}'", caixaId, usuarioFechamentoId);

        Caixa caixa = buscarPorId(caixaId);

        if (!caixa.estaAberto()) {
            throw new CaixaFechadoException("fechar o caixa");
        }

        Usuario usuarioFechamento = usuarioRepository.findById(usuarioFechamentoId)
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(Usuario.class, usuarioFechamentoId));

        caixa.setStatus(StatusCaixa.FECHADO);
        caixa.setFechadoEm(LocalDateTime.now());
        caixa.setUsuarioFechamento(usuarioFechamento);

        Caixa fechado = caixaRepository.save(caixa);
        log.info("Caixa '{}' fechado com sucesso", caixaId);
        return fechado;
    }

    /**
     * Reabre um caixa previamente fechado (privativo do Administrador do Restaurante).
     */
    @Transactional
    public Caixa reabrirCaixa(UUID caixaId, UUID usuarioReaberturaId, String motivoReabertura) {
        log.info("Solicitação de reabertura do caixa '{}' pelo usuário '{}'", caixaId, usuarioReaberturaId);

        Caixa caixa = buscarPorId(caixaId);

        if (!caixa.podeSerReaberto()) {
            throw new TransicaoEstadoInvalidaException(
                    "Caixa", caixa.getStatus(), StatusCaixa.REABERTO);
        }

        if (motivoReabertura == null || motivoReabertura.trim().isEmpty()) {
            throw new RegraDeNegocioException("É obrigatório informar a justificativa para a reabertura do caixa.");
        }

        Usuario usuarioReabertura = usuarioRepository.findById(usuarioReaberturaId)
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(Usuario.class, usuarioReaberturaId));

        caixa.setStatus(StatusCaixa.REABERTO);
        caixa.setReabertoEm(LocalDateTime.now());
        caixa.setUsuarioReabertura(usuarioReabertura);
        caixa.setMotivoReabertura(motivoReabertura.trim());

        Caixa reaberto = caixaRepository.save(caixa);
        log.info("Caixa '{}' reaberto com sucesso", caixaId);
        return reaberto;
    }
}
