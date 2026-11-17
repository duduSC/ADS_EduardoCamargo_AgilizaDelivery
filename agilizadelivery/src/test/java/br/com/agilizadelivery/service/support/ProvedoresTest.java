package br.com.agilizadelivery.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.agilizadelivery.exception.ProvedorDesconhecidoException;

class ProvedoresTest {

    @Test
    @DisplayName("Deve normalizar caixa e espaços dos provedores aceitos")
    void deveNormalizar() {
        assertEquals(Provedores.IFOOD, Provedores.normalizar("ifood"));
        assertEquals(Provedores.IFOOD, Provedores.normalizar(" iFood "));
        assertEquals(Provedores.SIMULADOR, Provedores.normalizar("SIMULADOR"));
        assertEquals(Set.of("IFOOD", "SIMULADOR"), Provedores.aceitos());
    }

    @Test
    @DisplayName("Deve recusar provedor nulo, vazio ou desconhecido")
    void deveRecusarInvalido() {
        assertThrows(ProvedorDesconhecidoException.class, () -> Provedores.normalizar(null));
        assertThrows(ProvedorDesconhecidoException.class, () -> Provedores.normalizar("  "));
        assertThrows(ProvedorDesconhecidoException.class, () -> Provedores.normalizar("RAPPI"));
    }
}
