package br.com.agilizadelivery.service.impl;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.dto.response.RelatorioDoDiaResponse;
import br.com.agilizadelivery.dto.response.RelatorioDoEntregadorResponse;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusRecibo;
import br.com.agilizadelivery.exception.RequisicaoInvalidaException;
import br.com.agilizadelivery.exception.handler.CodigoErro;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.service.RelatorioService;

/**
 * Relatorio simples do dia (D013, item 5), montado numa consulta so.
 *
 * O dia vai de 00:00 ate antes da meia-noite seguinte, no horario local
 * do contrato (HorarioLocal), pela data de finalizacao da entrega.
 *
 * PROVISORIO (D013 nao detalha):
 * - valorPago soma o repasse das entregas do dia que estao em recibo
 *   PAGO, e nao o valor dos recibos com data do dia. Para um recibo
 *   que so tem entregas do dia, os dois coincidem; quando o periodo do
 *   recibo passa da meia-noite, so assim o a pagar nunca fica negativo
 *   nem paga duas vezes a mesma entrega.
 * - entregadoresQueTrabalharam conta os entregadores com ao menos uma
 *   entrega finalizada no dia, inclusive cancelada depois do despacho:
 *   e o tamanho de porEntregador. A entrega cancelada antes do despacho
 *   nao tem entregador e so entra nos totais da loja.
 * - um repasse null (fora de area sem valor configurado, ou sem
 *   distancia) torna null o total e o a pagar de quem o contem, em vez
 *   de virar zero; o pago continua definido.
 */
@Service
@Transactional(readOnly = true)
public class RelatorioServiceImpl implements RelatorioService {

    /**
     * Ano de quatro digitos: o dia seguinte ao ultimo sempre existe, e o
     * PostgreSQL guarda o periodo (ano 999999999 nao cabe num timestamp).
     */
    private static final int PRIMEIRO_ANO = 1;
    private static final int ULTIMO_ANO = 9999;

    private final EntregaRepository entregaRepository;

    public RelatorioServiceImpl(EntregaRepository entregaRepository) {
        this.entregaRepository = entregaRepository;
    }

    @Override
    public RelatorioDoDiaResponse doDia(UUID estabelecimentoId, LocalDate data) {
        if (data.getYear() < PRIMEIRO_ANO || data.getYear() > ULTIMO_ANO) {
            throw new RequisicaoInvalidaException(
                    "A data do relatório deve ter o ano entre 1 e 9999.", CodigoErro.PARAMETRO_INVALIDO);
        }
        List<Entrega> entregas = entregaRepository
                .findFinalizadasNoPeriodoComEntregador(
                        estabelecimentoId, data.atStartOfDay(), data.plusDays(1).atStartOfDay())
                .stream()
                .filter(Entrega::emEstadoFinal)
                .toList();

        Map<UUID, Motoboy> entregadores = new LinkedHashMap<>();
        Map<UUID, List<Entrega>> porMotoboy = new LinkedHashMap<>();
        for (Entrega entrega : entregas) {
            if (entrega.getLote() == null) {
                continue;
            }
            Motoboy motoboy = entrega.getLote().getMotoboy();
            entregadores.putIfAbsent(motoboy.getId(), motoboy);
            porMotoboy.computeIfAbsent(motoboy.getId(), id -> new ArrayList<>()).add(entrega);
        }

        List<RelatorioDoEntregadorResponse> linhas = porMotoboy.entrySet().stream()
                .map(grupo -> linha(entregadores.get(grupo.getKey()), grupo.getValue()))
                .sorted(porNome())
                .toList();

        BigDecimal total = soma(entregas);
        BigDecimal pago = soma(pagas(entregas));
        return new RelatorioDoDiaResponse(
                data,
                entregas.size(),
                contar(entregas, StatusEntrega.ENTREGUE),
                contar(entregas, StatusEntrega.FALHA),
                contar(entregas, StatusEntrega.CANCELADA),
                linhas.size(),
                total,
                pago,
                diferenca(total, pago),
                linhas);
    }

    private static RelatorioDoEntregadorResponse linha(Motoboy motoboy, List<Entrega> entregas) {
        BigDecimal total = soma(entregas);
        BigDecimal pago = soma(pagas(entregas));
        return new RelatorioDoEntregadorResponse(
                motoboy.getId(),
                motoboy.getUsuario().getNome(),
                entregas.size(),
                contar(entregas, StatusEntrega.ENTREGUE),
                contar(entregas, StatusEntrega.FALHA),
                total,
                pago,
                diferenca(total, pago));
    }

    /** Ordem alfabetica do portugues; o Collator nao e compartilhado entre requisicoes. */
    private static Comparator<RelatorioDoEntregadorResponse> porNome() {
        return Comparator
                .comparing(RelatorioDoEntregadorResponse::nome, Collator.getInstance(Locale.of("pt", "BR")))
                .thenComparing(RelatorioDoEntregadorResponse::motoboyId);
    }

    private static List<Entrega> pagas(List<Entrega> entregas) {
        return entregas.stream()
                .filter(entrega -> entrega.getRecibo() != null
                        && entrega.getRecibo().getStatus() == StatusRecibo.PAGO)
                .toList();
    }

    private static int contar(List<Entrega> entregas, StatusEntrega status) {
        return (int) entregas.stream().filter(entrega -> entrega.getStatus() == status).count();
    }

    /** Soma do repasse; null se algum for indefinido (D013). */
    private static BigDecimal soma(List<Entrega> entregas) {
        BigDecimal total = BigDecimal.ZERO;
        for (Entrega entrega : entregas) {
            if (entrega.getValorRepasse() == null) {
                return null;
            }
            total = total.add(entrega.getValorRepasse());
        }
        return total;
    }

    private static BigDecimal diferenca(BigDecimal total, BigDecimal pago) {
        return total == null || pago == null ? null : total.subtract(pago);
    }
}
