package br.com.agilizadelivery.service.support;

import static br.com.agilizadelivery.service.Fixtures.reais;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import br.com.agilizadelivery.entity.model.enums.MotivoRevisao;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.service.Fixtures;
import br.com.agilizadelivery.service.rota.ServicoDeGeocodificacao;
import br.com.agilizadelivery.service.rota.ServicoDeRotas;

@ExtendWith(MockitoExtension.class)
class LocalizacaoEntregaTest {

    @Mock
    private ServicoDeGeocodificacao geocodificacao;

    @Mock
    private ServicoDeRotas rotas;

    @Mock
    private CalculadoraRepasse calculadoraRepasse;

    private LocalizacaoEntrega localizacao;
    private Entrega entrega;

    @BeforeEach
    void setUp() {
        localizacao = new LocalizacaoEntrega(geocodificacao, rotas, calculadoraRepasse);
        Estabelecimento estabelecimento = Fixtures.estabelecimento();
        entrega = Fixtures.entrega(estabelecimento, StatusEntrega.AGUARDANDO_DESPACHO);
        entrega.setDistanciaKm(null);
        entrega.setValorRepasse(null);
    }

    private void localizar() {
        localizacao.aplicar(entrega, localizacao.consultar(entrega.getCoordenada(), entrega.getEnderecoCompleto(),
                entrega.getEstabelecimento().getCoordenada()));
    }

    @Test
    @DisplayName("Deve calcular a distância loja -> cliente e aplicar a faixa quando há coordenada")
    void deveCalcularDistanciaEAplicarFaixa() {
        when(rotas.distanciaKm(any(), any())).thenReturn(Optional.of(reais("4.70")));

        localizar();

        assertEquals(reais("4.70"), entrega.getDistanciaKm());
        assertFalse(entrega.getPendenteRevisao());
        verify(calculadoraRepasse).aplicarFaixaVigente(entrega);
        verifyNoInteractions(geocodificacao);
    }

    @Test
    @DisplayName("Deve geocodificar o endereço quando a coordenada não foi informada")
    void deveGeocodificarEndereco() {
        Coordenada encontrada = new Coordenada(reais("-23.56"), reais("-46.65"));
        entrega.setCoordenada(null);
        when(geocodificacao.geocodificar(entrega.getEnderecoCompleto())).thenReturn(Optional.of(encontrada));
        when(rotas.distanciaKm(any(), any())).thenReturn(Optional.of(reais("2.10")));

        localizar();

        assertSame(encontrada, entrega.getCoordenada());
        assertFalse(entrega.getPendenteRevisao());
    }

    @Test
    @DisplayName("Deve marcar SEM_COORDENADA quando a geocodificação falha, sem consultar rotas")
    void deveMarcarSemCoordenada() {
        entrega.setCoordenada(null);
        when(geocodificacao.geocodificar(entrega.getEnderecoCompleto())).thenReturn(Optional.empty());

        localizar();

        assertTrue(entrega.getPendenteRevisao());
        assertEquals(MotivoRevisao.SEM_COORDENADA, entrega.getMotivoRevisao());
        assertNull(entrega.getValorRepasse());
        verifyNoInteractions(rotas);
        verify(calculadoraRepasse, never()).aplicarFaixaVigente(any());
    }

    @Test
    @DisplayName("Deve marcar SEM_DISTANCIA quando a Routes API não retorna rota")
    void deveMarcarSemDistancia() {
        when(rotas.distanciaKm(any(), any())).thenReturn(Optional.empty());

        localizar();

        assertTrue(entrega.getPendenteRevisao());
        assertEquals(MotivoRevisao.SEM_DISTANCIA, entrega.getMotivoRevisao());
        assertNull(entrega.getDistanciaKm());
    }

    @Test
    @DisplayName("Deve limpar pendência de localização após o reposicionamento bem-sucedido")
    void deveLimparPendenciaDeLocalizacao() {
        entrega.setPendenteRevisao(true);
        entrega.setMotivoRevisao(MotivoRevisao.SEM_DISTANCIA);
        when(rotas.distanciaKm(any(), any())).thenReturn(Optional.of(reais("3.00")));

        localizar();

        assertFalse(entrega.getPendenteRevisao());
        assertNull(entrega.getMotivoRevisao());
    }

    @Test
    @DisplayName("Não deve limpar a contestação do entregador ao recalcular a localização")
    void naoDeveLimparContestacao() {
        entrega.contestar();
        when(rotas.distanciaKm(any(), any())).thenReturn(Optional.of(reais("3.00")));

        localizar();

        assertTrue(entrega.getPendenteRevisao());
        assertEquals(MotivoRevisao.CONTESTACAO_ENTREGADOR, entrega.getMotivoRevisao());
    }
}
