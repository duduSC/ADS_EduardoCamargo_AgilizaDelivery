package br.com.agilizadelivery.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.PedidoExterno;
import br.com.agilizadelivery.exception.PedidoJaImportadoException;
import br.com.agilizadelivery.service.PedidoExternoService;
import br.com.agilizadelivery.service.support.Provedores;

/**
 * Idempotencia de PEDIDO_EXTERNO com provedor em texto (DVP 3.0), contra o
 * banco H2 do perfil de testes.
 */
@SpringBootTest
@Transactional
class PedidoExternoPersistenciaTest {

    @Autowired
    private PedidoExternoService service;

    @Autowired
    private PedidoExternoRepository repository;

    @Autowired
    private EstabelecimentoRepository estabelecimentoRepository;

    private Estabelecimento estabelecimento;

    @BeforeEach
    void setUp() {
        estabelecimento = estabelecimentoRepository.saveAndFlush(EstabelecimentoPersistenciaTest.novoEstabelecimento());
    }

    private static PedidoExterno pedido(String provedor, String idExterno) {
        PedidoExterno pedido = new PedidoExterno();
        pedido.setProvedor(provedor);
        pedido.setIdExterno(idExterno);
        pedido.setNomeCliente("Cliente");
        pedido.setValorTotal(new BigDecimal("59.90"));
        pedido.setPayloadJson("{}");
        return pedido;
    }

    @Test
    @DisplayName("\"ifood\" e \"IFOOD\" resultam no mesmo registro de idempotência")
    void deveTratarCaixaDoProvedorComoMesmaChave() {
        String idExterno = "pedido-" + UUID.randomUUID();

        service.importar(estabelecimento.getId(), pedido("ifood", idExterno));
        repository.flush();

        assertThrows(PedidoJaImportadoException.class, () ->
                service.importar(estabelecimento.getId(), pedido("IFOOD", idExterno)));

        PedidoExterno gravado = repository.findByProvedorAndIdExterno(Provedores.IFOOD, idExterno).orElseThrow();
        assertEquals(Provedores.IFOOD, gravado.getProvedor());
        assertTrue(service.jaImportado("iFood", idExterno));
        assertEquals(1, repository.findByEstabelecimentoIdAndProcessadoEmIsNull(estabelecimento.getId()).size());
    }
}
