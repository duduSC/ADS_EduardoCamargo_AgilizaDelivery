package br.com.agilizadelivery.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.dto.CoordenadaDto;
import br.com.agilizadelivery.dto.DtoMapper;
import br.com.agilizadelivery.dto.request.AlterarStatusEntregaRequest;
import br.com.agilizadelivery.dto.request.CriarEntregaAvulsaRequest;
import br.com.agilizadelivery.dto.response.EntregaResponse;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import br.com.agilizadelivery.entity.model.enums.MotivoRevisao;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.exception.AcessoNegadoException;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.exception.TransicaoEstadoInvalidaException;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.service.EntregaService;
import br.com.agilizadelivery.service.support.AjusteRepasse;
import br.com.agilizadelivery.service.support.BuscadorTenant;
import br.com.agilizadelivery.service.support.CalculadoraRepasse;
import br.com.agilizadelivery.service.support.Coordenadas;
import br.com.agilizadelivery.service.support.EncerramentoLote;
import br.com.agilizadelivery.service.support.LocalizacaoEntrega;
import br.com.agilizadelivery.service.support.PoliticaCorrecao;
import br.com.agilizadelivery.service.support.TransicaoEntrega;

/**
 * Ciclo de vida da entrega (UC01, UC02, UC08) e do seu repasse (DVP 1.5.5).
 *
 * Toda mudanca de status passa por TransicaoEntrega, que aplica a maquina
 * de estados e grava o historico. O despacho e a devolucao, por mexerem
 * no lote, ficam no LoteEntregaService.
 */
@Service
@Transactional(readOnly = true)
public class EntregaServiceImpl implements EntregaService {

    private static final int TAMANHO_MOTIVO = 100;

    private final EntregaRepository repository;
    private final BuscadorTenant buscador;
    private final LocalizacaoEntrega localizacao;
    private final CalculadoraRepasse calculadoraRepasse;
    private final TransicaoEntrega transicao;
    private final EncerramentoLote encerramentoLote;
    private final PoliticaCorrecao politicaCorrecao;
    private final AjusteRepasse ajusteRepasse;

    public EntregaServiceImpl(
            EntregaRepository repository,
            BuscadorTenant buscador,
            LocalizacaoEntrega localizacao,
            CalculadoraRepasse calculadoraRepasse,
            TransicaoEntrega transicao,
            EncerramentoLote encerramentoLote,
            PoliticaCorrecao politicaCorrecao,
            AjusteRepasse ajusteRepasse) {
        this.repository = repository;
        this.buscador = buscador;
        this.localizacao = localizacao;
        this.calculadoraRepasse = calculadoraRepasse;
        this.transicao = transicao;
        this.encerramentoLote = encerramentoLote;
        this.politicaCorrecao = politicaCorrecao;
        this.ajusteRepasse = ajusteRepasse;
    }

    /** Pedido avulso, digitado pelo operador (UC01 A2). */
    @Override
    @Transactional
    public EntregaResponse criarAvulsa(UUID estabelecimentoId, CriarEntregaAvulsaRequest request) {
        Entrega entrega = new Entrega();
        entrega.setEstabelecimento(buscador.estabelecimentoAtivo(estabelecimentoId));
        entrega.setNomeCliente(request.nomeCliente());
        entrega.setTelefoneCliente(request.telefoneCliente());
        entrega.setEnderecoCompleto(request.enderecoCompleto());
        entrega.setBairro(request.bairro());
        entrega.setCep(request.cep());
        entrega.setComplemento(request.complemento());
        entrega.setPontoReferencia(request.pontoReferencia());
        entrega.setCoordenada(coordenadaInformada(request.coordenada()));
        entrega.setValorPedido(request.valorPedido());
        entrega.setFormaPagamento(request.formaPagamento());
        entrega.setPagoOnline(Boolean.TRUE.equals(request.pagoOnline()));
        entrega.setTrocoPara(request.trocoPara());
        entrega.setObservacoes(request.observacoes());
        entrega.setStatus(StatusEntrega.AGUARDANDO_DESPACHO);
        entrega.setPendenteRevisao(false);

        localizacao.localizar(entrega);
        Entrega salva = repository.save(entrega);
        transicao.registrarCriacao(salva, null);
        return DtoMapper.toResponse(salva);
    }

    @Override
    public EntregaResponse buscarPorId(UUID estabelecimentoId, UUID entregaId) {
        return DtoMapper.toResponse(buscar(estabelecimentoId, entregaId));
    }

    @Override
    public List<EntregaResponse> listarPorEstabelecimentoEStatus(UUID estabelecimentoId, StatusEntrega status) {
        return paraResposta(repository.findByEstabelecimentoIdAndStatusOrderByCriadoEmAsc(estabelecimentoId, status));
    }

    /** Mapa de pendentes: sem revisao em aberto (HU01). */
    @Override
    public List<EntregaResponse> listarPendentesDeDespacho(UUID estabelecimentoId) {
        return paraResposta(repository.findByEstabelecimentoIdAndStatusAndPendenteRevisaoFalseOrderByCriadoEmAsc(
                estabelecimentoId, StatusEntrega.AGUARDANDO_DESPACHO));
    }

    /** Extrato do entregador no periodo (UC08). */
    @Override
    public List<EntregaResponse> listarDoMotoboy(
            UUID estabelecimentoId,
            UUID motoboyId,
            LocalDateTime inicio,
            LocalDateTime fim) {
        if (inicio == null || fim == null || fim.isBefore(inicio)) {
            throw new RegraDeNegocioException("O período informado é inválido.");
        }
        buscador.motoboy(estabelecimentoId, motoboyId);
        return paraResposta(repository
                .findByEstabelecimentoIdAndLoteMotoboyIdAndFinalizadaEmBetweenOrderByFinalizadaEmAsc(
                        estabelecimentoId, motoboyId, inicio, fim));
    }

    /** A cozinha concluiu o pedido (UC01, passo 5). */
    @Override
    @Transactional
    public EntregaResponse marcarProntaParaDespacho(UUID estabelecimentoId, UUID entregaId, LocalDateTime prontaEm) {
        Entrega entrega = buscar(estabelecimentoId, entregaId);
        if (entrega.getStatus() != StatusEntrega.AGUARDANDO_DESPACHO) {
            throw new TransicaoEstadoInvalidaException(
                    "Somente entregas aguardando despacho podem ser marcadas como prontas.");
        }
        entrega.setProntoParaDespachoEm(prontaEm == null ? LocalDateTime.now() : prontaEm);
        return DtoMapper.toResponse(repository.save(entrega));
    }

    /**
     * Registro de ENTREGUE ou FALHA pelo entregador (UC02, HU05). O repasse
     * e congelado com a faixa vigente nesse momento (DVP 1.5.5, passo 4).
     */
    @Override
    @Transactional
    public EntregaResponse alterarStatus(
            UUID estabelecimentoId,
            UUID entregaId,
            UUID usuarioId,
            AlterarStatusEntregaRequest request) {
        StatusEntrega destino = request.status();
        if (destino != StatusEntrega.ENTREGUE && destino != StatusEntrega.FALHA) {
            throw new TransicaoEstadoInvalidaException(
                    "Por esta operação a entrega só pode ser finalizada como ENTREGUE ou FALHA; "
                            + "despacho, início de rota e cancelamento têm operações próprias.");
        }
        if (destino == StatusEntrega.FALHA && (request.motivoFalha() == null || request.motivoFalha().isBlank())) {
            throw new RegraDeNegocioException("O motivo da falha é obrigatório (UC02 A1).");
        }

        Entrega entrega = buscar(estabelecimentoId, entregaId);
        Usuario autor = buscador.usuario(estabelecimentoId, usuarioId);
        exigirEntregadorDoLote(entrega, autor);

        LocalDateTime ocorridoEm = request.ocorridoEm() == null ? LocalDateTime.now() : request.ocorridoEm();
        transicao.transitar(entrega, destino, autor, coordenadaInformada(request.coordenada()), ocorridoEm);
        entrega.setMotivoFalha(destino == StatusEntrega.FALHA ? request.motivoFalha().trim() : null);
        entrega.setFinalizadaEm(ocorridoEm);
        calculadoraRepasse.aplicarFaixaVigente(entrega);

        Entrega salva = repository.save(entrega);
        encerramentoLote.avaliar(salva.getLote());
        return DtoMapper.toResponse(salva);
    }

    /**
     * Cancelamento pelo operador (UC02 A3). Antes da saida o repasse e zero;
     * a partir de EM_ROTA e integral e congelado (DVP 1.5.5, passo 5).
     */
    @Override
    @Transactional
    public EntregaResponse cancelar(UUID estabelecimentoId, UUID entregaId, UUID usuarioId, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new RegraDeNegocioException("O motivo do cancelamento é obrigatório.");
        }
        Entrega entrega = buscar(estabelecimentoId, entregaId);
        Usuario autor = buscador.usuarioComPerfil(
                estabelecimentoId, usuarioId, PerfilUsuario.OPERADOR, PerfilUsuario.ADMIN_RESTAURANTE);

        boolean saiuDaLoja = entrega.getStatus() == StatusEntrega.EM_ROTA;
        transicao.transitar(entrega, StatusEntrega.CANCELADA, autor, null, LocalDateTime.now());
        if (saiuDaLoja) {
            calculadoraRepasse.aplicarFaixaVigente(entrega);
        } else {
            calculadoraRepasse.zerarRepasse(entrega);
            entrega.setPendenteRevisao(false);
            entrega.setMotivoRevisao(null);
        }
        entrega.setMotivoFalha(limitar(motivo.trim()));
        entrega.setFinalizadaEm(LocalDateTime.now());

        Entrega salva = repository.save(entrega);
        encerramentoLote.avaliar(salva.getLote());
        return DtoMapper.toResponse(salva);
    }

    /** Reposicionamento manual do pino (UC01 A1, risco R04). */
    @Override
    @Transactional
    public EntregaResponse ajustarCoordenada(
            UUID estabelecimentoId,
            UUID entregaId,
            BigDecimal latitude,
            BigDecimal longitude,
            UUID usuarioId) {
        Entrega entrega = buscar(estabelecimentoId, entregaId);
        buscador.usuarioComPerfil(
                estabelecimentoId, usuarioId, PerfilUsuario.OPERADOR, PerfilUsuario.ADMIN_RESTAURANTE);
        if (entrega.emEstadoFinal()) {
            throw new RegraDeNegocioException("Não é possível reposicionar uma entrega já finalizada.");
        }

        entrega.setCoordenada(Coordenadas.validada(latitude, longitude));
        localizacao.localizar(entrega);
        Entrega salva = repository.save(entrega);
        encerramentoLote.atualizarTotal(salva.getLote());
        return DtoMapper.toResponse(salva);
    }

    /** Contestacao do valor pelo entregador (UC08 A1, E1 e E2). */
    @Override
    @Transactional
    public EntregaResponse contestar(UUID estabelecimentoId, UUID entregaId, UUID motoboyId) {
        Entrega entrega = buscar(estabelecimentoId, entregaId);
        buscador.motoboy(estabelecimentoId, motoboyId);
        if (entrega.getLote() == null || !motoboyId.equals(entrega.getLote().getMotoboy().getId())) {
            throw new AcessoNegadoException("A entrega não pertence a este entregador.");
        }
        if (Boolean.TRUE.equals(entrega.getPendenteRevisao())) {
            throw new RegraDeNegocioException("A entrega já está sob análise do operador.");
        }
        if (!entrega.emEstadoFinal()) {
            throw new RegraDeNegocioException("Somente entregas concluídas podem ter o valor contestado.");
        }
        politicaCorrecao.exigirCaixaDoDiaAberto(entrega, "contestar o valor da entrega");
        if (!entrega.podeSerContestada()) {
            throw new RegraDeNegocioException("A entrega não pode ser contestada.");
        }

        entrega.contestar();
        return DtoMapper.toResponse(repository.save(entrega));
    }

    /**
     * Analise da contestacao pelo operador. Se procedente, o valor e
     * ajustado pelo UC03 A1, que exige a entrega fora de recibo e o caixa
     * do dia aberto. Pendencias de localizacao se resolvem pelo pino.
     */
    @Override
    @Transactional
    public EntregaResponse resolverRevisao(
            UUID estabelecimentoId,
            UUID entregaId,
            UUID usuarioId,
            BigDecimal valorRepasse,
            String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new RegraDeNegocioException("O motivo da resolução da revisão é obrigatório.");
        }
        Entrega entrega = buscar(estabelecimentoId, entregaId);
        Usuario autor = buscador.usuarioComPerfil(
                estabelecimentoId, usuarioId, PerfilUsuario.OPERADOR, PerfilUsuario.ADMIN_RESTAURANTE);
        if (!Boolean.TRUE.equals(entrega.getPendenteRevisao())) {
            throw new RegraDeNegocioException("A entrega não possui revisão pendente.");
        }
        if (entrega.getMotivoRevisao() != MotivoRevisao.CONTESTACAO_ENTREGADOR) {
            throw new RegraDeNegocioException(
                    "Pendências de localização são resolvidas reposicionando o pino da entrega.");
        }

        if (valorRepasse != null && !mesmoValor(valorRepasse, entrega.getValorRepasse())) {
            ajusteRepasse.ajustar(entrega, autor, valorRepasse, motivo);
        } else {
            politicaCorrecao.exigirCaixaDoDiaAberto(entrega, "resolver a revisão da entrega");
        }
        entrega.setPendenteRevisao(false);
        entrega.setMotivoRevisao(null);
        return DtoMapper.toResponse(repository.save(entrega));
    }

    private Entrega buscar(UUID estabelecimentoId, UUID entregaId) {
        return repository.findByIdAndEstabelecimentoId(entregaId, estabelecimentoId)
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(Entrega.class, entregaId));
    }

    /** O entregador so atualiza as entregas do proprio lote (HU09). */
    private static void exigirEntregadorDoLote(Entrega entrega, Usuario autor) {
        if (autor.getPerfil() != PerfilUsuario.ENTREGADOR) {
            return;
        }
        boolean doLote = entrega.getLote() != null
                && autor.getId().equals(entrega.getLote().getMotoboy().getUsuario().getId());
        if (!doLote) {
            throw new AcessoNegadoException("A entrega não pertence a este entregador.");
        }
    }

    private static Coordenada coordenadaInformada(CoordenadaDto dto) {
        if (dto == null || dto.latitude() == null || dto.longitude() == null) {
            return null;
        }
        return Coordenadas.validada(dto.latitude(), dto.longitude());
    }

    private static boolean mesmoValor(BigDecimal a, BigDecimal b) {
        return b != null && a.compareTo(b) == 0;
    }

    private static String limitar(String texto) {
        return texto.length() <= TAMANHO_MOTIVO ? texto : texto.substring(0, TAMANHO_MOTIVO);
    }

    private static List<EntregaResponse> paraResposta(List<Entrega> entregas) {
        return entregas.stream().map(DtoMapper::toResponse).toList();
    }
}
