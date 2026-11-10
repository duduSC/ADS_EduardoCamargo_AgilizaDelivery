package br.com.agilizadelivery.entity.model.enums;

import static br.com.agilizadelivery.entity.model.enums.StatusEntrega.AGUARDANDO_DESPACHO;
import static br.com.agilizadelivery.entity.model.enums.StatusEntrega.CANCELADA;
import static br.com.agilizadelivery.entity.model.enums.StatusEntrega.DESPACHADA;
import static br.com.agilizadelivery.entity.model.enums.StatusEntrega.EM_ROTA;
import static br.com.agilizadelivery.entity.model.enums.StatusEntrega.ENTREGUE;
import static br.com.agilizadelivery.entity.model.enums.StatusEntrega.FALHA;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatusEntregaTest {

    /** Tabela de transicoes do DVP 1.4.4. */
    private static final Map<StatusEntrega, Set<StatusEntrega>> PERMITIDAS = Map.of(
            AGUARDANDO_DESPACHO, EnumSet.of(DESPACHADA, CANCELADA),
            DESPACHADA, EnumSet.of(EM_ROTA, AGUARDANDO_DESPACHO, CANCELADA),
            EM_ROTA, EnumSet.of(ENTREGUE, FALHA, CANCELADA),
            ENTREGUE, EnumSet.noneOf(StatusEntrega.class),
            FALHA, EnumSet.noneOf(StatusEntrega.class),
            CANCELADA, EnumSet.noneOf(StatusEntrega.class));

    @Test
    @DisplayName("Deve admitir exatamente as transições da máquina de estados do DVP 1.4.4")
    void deveSeguirATabelaDeTransicoes() {
        for (StatusEntrega origem : StatusEntrega.values()) {
            for (StatusEntrega destino : StatusEntrega.values()) {
                assertEquals(PERMITIDAS.get(origem).contains(destino), origem.podeTransitarPara(destino),
                        origem + " -> " + destino);
            }
        }
    }

    @Test
    @DisplayName("Deve considerar finais apenas ENTREGUE, FALHA e CANCELADA")
    void deveIdentificarEstadosFinais() {
        for (StatusEntrega status : StatusEntrega.values()) {
            assertEquals(PERMITIDAS.get(status).isEmpty(), status.finalizado(), status.name());
        }
    }
}
