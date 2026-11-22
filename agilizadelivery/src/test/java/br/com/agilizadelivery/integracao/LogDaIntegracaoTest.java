package br.com.agilizadelivery.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.client.RestClient;

import br.com.agilizadelivery.entity.model.ConfiguracaoIntegracao;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import br.com.agilizadelivery.integracao.maps.GoogleGeocodingServicoDeGeocodificacao;
import br.com.agilizadelivery.integracao.maps.GoogleMapsProperties;
import br.com.agilizadelivery.integracao.pedidos.CicloIntegracao;
import br.com.agilizadelivery.integracao.pedidos.IntegracaoPedidosProperties;
import br.com.agilizadelivery.integracao.pedidos.PedidoRecebido;
import br.com.agilizadelivery.integracao.pedidos.ProvedorDePedidos;
import br.com.agilizadelivery.repository.ConfiguracaoIntegracaoRepository;
import br.com.agilizadelivery.repository.FalhaIntegracaoRepository;
import br.com.agilizadelivery.service.support.ImportacaoPedido;

/** As integracoes tambem nao logam telefone nem segredo (Constituicao 6.1 a 6.3). */
@ExtendWith(OutputCaptureExtension.class)
class LogDaIntegracaoTest {

    @Test
    @DisplayName("Falha do worker não registra o telefone que veio na mensagem do banco")
    void telefoneForaDoLog(CapturedOutput saida) {
        String telefone = "54987654321";
        ProvedorDePedidos provedor = mock(ProvedorDePedidos.class);
        ConfiguracaoIntegracaoRepository configuracoes = mock(ConfiguracaoIntegracaoRepository.class);
        ImportacaoPedido importacao = mock(ImportacaoPedido.class);
        Estabelecimento loja = new Estabelecimento();
        loja.setId(UUID.randomUUID());
        loja.setStatus(StatusEstabelecimento.ATIVO);
        ConfiguracaoIntegracao configuracao = new ConfiguracaoIntegracao();
        configuracao.setId(UUID.randomUUID());
        configuracao.setEstabelecimento(loja);
        configuracao.setProvedor("SIMULADOR");
        PedidoRecebido pedido = new PedidoRecebido("ext-1", "1", "PLACED", "Cliente", telefone, "Rua", null, null,
                null, null, null, null, null, BigDecimal.TEN, null, true, null, null, List.of(), "{}");
        when(provedor.provedor()).thenReturn("SIMULADOR");
        when(configuracoes.findAtivasComEstabelecimento()).thenReturn(List.of(configuracao));
        when(provedor.buscarNovosPedidos(any())).thenReturn(List.of(pedido));
        when(importacao.importar(any(), any())).thenThrow(
                new DataIntegrityViolationException("Value too long for TELEFONE_CLIENTE: '" + telefone + "'"));
        CicloIntegracao ciclo = new CicloIntegracao(List.of(provedor), configuracoes,
                mock(FalhaIntegracaoRepository.class), importacao, new IntegracaoPedidosProperties(
                        new IntegracaoPedidosProperties.Worker(false, Duration.ofSeconds(30), Duration.ZERO, 2),
                        null));

        ciclo.executar();

        assertThat(saida.getAll()).contains("PERSISTENCIA").doesNotContain(telefone);
    }

    @Test
    @DisplayName("Falha de rede no geocoding não registra a chave nem o endereço do cliente")
    void chaveForaDoLog(CapturedOutput saida) {
        GoogleMapsProperties propriedades = new GoogleMapsProperties("chave-secreta-do-google", null,
                "http://127.0.0.1:1/geocode", Duration.ofSeconds(1), 10);

        new GoogleGeocodingServicoDeGeocodificacao(propriedades, RestClient.builder())
                .geocodificar("Rua do Cliente Sigiloso, 10");

        assertThat(saida.getAll()).contains("Geocoding")
                .doesNotContain("chave-secreta-do-google")
                .doesNotContain("Sigiloso");
    }
}
