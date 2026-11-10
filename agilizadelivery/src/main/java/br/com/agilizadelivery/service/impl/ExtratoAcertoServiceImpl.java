package br.com.agilizadelivery.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.dto.response.ExtratoAcertoResponse;
import br.com.agilizadelivery.dto.response.LinhaExtratoResponse;
import br.com.agilizadelivery.dto.response.SituacaoAcerto;
import br.com.agilizadelivery.entity.model.DistanciaPreco;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.service.ExtratoAcertoService;
import br.com.agilizadelivery.service.support.BuscadorTenant;

/**
 * Monta o extrato a partir das entregas finalizadas do entregador.
 *
 * A separacao entre devido, consolidado e em revisao segue a mesma regra
 * do recibo (Entrega.podeEntrarEmRecibo): o total devido e exatamente o
 * que o recibo gerado para o periodo somara.
 */
@Service
@Transactional(readOnly = true)
public class ExtratoAcertoServiceImpl implements ExtratoAcertoService {

    private final EntregaRepository entregaRepository;
    private final BuscadorTenant buscador;

    public ExtratoAcertoServiceImpl(EntregaRepository entregaRepository, BuscadorTenant buscador) {
        this.entregaRepository = entregaRepository;
        this.buscador = buscador;
    }

    @Override
    public ExtratoAcertoResponse gerarExtrato(
            UUID estabelecimentoId,
            UUID motoboyId,
            LocalDateTime inicio,
            LocalDateTime fim) {
        if (inicio == null || fim == null || !fim.isAfter(inicio)) {
            throw new RegraDeNegocioException("O período informado é inválido.");
        }
        Motoboy motoboy = buscador.motoboy(estabelecimentoId, motoboyId);

        List<LinhaExtratoResponse> linhas = entregaRepository
                .findByEstabelecimentoIdAndLoteMotoboyIdAndFinalizadaEmBetweenOrderByFinalizadaEmAsc(
                        estabelecimentoId, motoboyId, inicio, fim)
                .stream()
                .filter(Entrega::emEstadoFinal)
                .map(ExtratoAcertoServiceImpl::linha)
                .toList();

        return new ExtratoAcertoResponse(
                motoboy.getId(),
                motoboy.getUsuario().getNome(),
                inicio,
                fim,
                linhas,
                total(linhas, SituacaoAcerto.A_ACERTAR),
                total(linhas, SituacaoAcerto.CONSOLIDADA),
                total(linhas, SituacaoAcerto.EM_REVISAO),
                linhas.stream().anyMatch(linha ->
                        linha.situacao() == SituacaoAcerto.A_ACERTAR && linha.valorRepasse() == null));
    }

    private static LinhaExtratoResponse linha(Entrega entrega) {
        DistanciaPreco faixa = entrega.getFaixaPreco();
        return new LinhaExtratoResponse(
                entrega.getId(),
                entrega.getNomeCliente(),
                entrega.getFinalizadaEm(),
                entrega.getStatus(),
                entrega.getDistanciaKm(),
                faixa == null ? null : faixa.getDeKm(),
                faixa == null ? null : faixa.getAteKm(),
                foraDeArea(entrega),
                entrega.getValorRepasse(),
                situacao(entrega),
                entrega.getRecibo() == null ? null : entrega.getRecibo().getId());
    }

    private static SituacaoAcerto situacao(Entrega entrega) {
        if (Boolean.TRUE.equals(entrega.getPendenteRevisao())) {
            return SituacaoAcerto.EM_REVISAO;
        }
        return entrega.getRecibo() == null ? SituacaoAcerto.A_ACERTAR : SituacaoAcerto.CONSOLIDADA;
    }

    /**
     * Distancia conhecida e nenhuma faixa aplicada (DVP 1.5.5, passo 3).
     * O cancelamento antes da saida tambem fica sem faixa, mas com repasse
     * zero, e nao conta como fora de area.
     */
    private static boolean foraDeArea(Entrega entrega) {
        boolean semDeslocamento = entrega.getStatus() == StatusEntrega.CANCELADA
                && (entrega.getValorRepasse() == null || entrega.getValorRepasse().signum() == 0);
        return entrega.getFaixaPreco() == null && entrega.getDistanciaKm() != null && !semDeslocamento;
    }

    private static BigDecimal total(List<LinhaExtratoResponse> linhas, SituacaoAcerto situacao) {
        return linhas.stream()
                .filter(linha -> linha.situacao() == situacao)
                .map(LinhaExtratoResponse::valorRepasse)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
