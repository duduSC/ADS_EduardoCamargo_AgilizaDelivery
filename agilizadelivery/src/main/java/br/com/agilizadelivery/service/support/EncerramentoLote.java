package br.com.agilizadelivery.service.support;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import br.com.agilizadelivery.dto.DtoMapper;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.LoteEntrega;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusLote;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.LoteEntregaRepository;
import br.com.agilizadelivery.repository.MotoboyRepository;
import br.com.agilizadelivery.websocket.PublicadorTempoReal;

/**
 * Encerra o lote quando todas as suas entregas atingem estado final
 * (DVP 1.4.4): EM_ROTA -> CONCLUIDO, ou DESPACHADO -> CANCELADO quando
 * todas foram canceladas antes da saida. O entregador volta a ONLINE, e
 * a mudanca e publicada na frota da loja (HU05, T009).
 */
@Component
public class EncerramentoLote {

    private final EntregaRepository entregaRepository;
    private final LoteEntregaRepository loteRepository;
    private final MotoboyRepository motoboyRepository;
    private final PublicadorTempoReal publicador;

    public EncerramentoLote(
            EntregaRepository entregaRepository,
            LoteEntregaRepository loteRepository,
            MotoboyRepository motoboyRepository,
            PublicadorTempoReal publicador) {
        this.entregaRepository = entregaRepository;
        this.loteRepository = loteRepository;
        this.motoboyRepository = motoboyRepository;
        this.publicador = publicador;
    }

    /** Recalcula o total do lote e o encerra se nao houver entrega em aberto. */
    public void avaliar(LoteEntrega lote) {
        if (lote == null || (lote.getStatus() != StatusLote.EM_ROTA && lote.getStatus() != StatusLote.DESPACHADO)) {
            return;
        }

        List<Entrega> entregas = entregaRepository.findByLoteIdOrderByOrdemRotaAsc(lote.getId());
        lote.setValorTotalRepasse(somarRepasse(entregas));

        if (entregas.isEmpty() || !entregas.stream().allMatch(Entrega::emEstadoFinal)) {
            loteRepository.save(lote);
            return;
        }

        lote.setStatus(lote.getStatus() == StatusLote.EM_ROTA ? StatusLote.CONCLUIDO : StatusLote.CANCELADO);
        lote.setConcluidoEm(LocalDateTime.now());
        loteRepository.save(lote);
        liberarMotoboy(lote);
    }

    /** Mantem o total do lote em dia apos um ajuste manual de repasse. */
    public void atualizarTotal(LoteEntrega lote) {
        if (lote == null) {
            return;
        }
        lote.setValorTotalRepasse(somarRepasse(entregaRepository.findByLoteIdOrderByOrdemRotaAsc(lote.getId())));
        loteRepository.save(lote);
    }

    /** Soma do valorRepasse das entregas (DVP 1.5.5, passo 7). */
    public static BigDecimal somarRepasse(List<Entrega> entregas) {
        return entregas.stream()
                .map(Entrega::getValorRepasse)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void liberarMotoboy(LoteEntrega lote) {
        Motoboy motoboy = lote.getMotoboy();
        if (StatusDisponibilidadeMotoboy.EM_ROTA.equals(motoboy.getStatusDisponibilidade())) {
            motoboy.setStatusDisponibilidade(StatusDisponibilidadeMotoboy.ONLINE);
            motoboyRepository.save(motoboy);
            publicador.publicarFrota(
                    lote.getEstabelecimento().getId(), "DISPONIBILIDADE_ALTERADA", DtoMapper.toResponse(motoboy));
        }
    }
}
