package br.com.agilizadelivery.service;

import static br.com.agilizadelivery.service.Fixtures.reais;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.agilizadelivery.dto.request.AjustarRepasseRequest;
import br.com.agilizadelivery.dto.response.EntregaAjusteValorResponse;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.EntregaAjusteValor;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.exception.AcessoNegadoException;
import br.com.agilizadelivery.exception.AjusteValorNaoPermitidoException;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.repository.EntregaAjusteValorRepository;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.service.impl.EntregaAjusteValorServiceImpl;
import br.com.agilizadelivery.service.support.AjusteRepasse;
import br.com.agilizadelivery.service.support.BuscadorTenant;

@ExtendWith(MockitoExtension.class)
class EntregaAjusteValorServiceTest {

    @Mock
    private EntregaAjusteValorRepository repository;

    @Mock
    private EntregaRepository entregaRepository;

    @Mock
    private BuscadorTenant buscador;

    @Mock
    private AjusteRepasse ajusteRepasse;

    private EntregaAjusteValorService service;
    private Estabelecimento estabelecimento;
    private Entrega entrega;
    private Usuario operador;

    @BeforeEach
    void setUp() {
        service = new EntregaAjusteValorServiceImpl(repository, entregaRepository, buscador, ajusteRepasse);
        estabelecimento = Fixtures.estabelecimento();
        entrega = Fixtures.entrega(estabelecimento, StatusEntrega.ENTREGUE);
        operador = Fixtures.usuario(estabelecimento, PerfilUsuario.OPERADOR);
    }

    private EntregaAjusteValor ajuste() {
        EntregaAjusteValor ajuste = new EntregaAjusteValor();
        ajuste.setId(1L);
        ajuste.setEntrega(entrega);
        ajuste.setUsuario(operador);
        ajuste.setValorAnterior(reais("9.00"));
        ajuste.setValorNovo(reais("12.00"));
        ajuste.setMotivo("Divergência");
        return ajuste;
    }

    private void entregaEOperadorEncontrados() {
        when(entregaRepository.findByIdAndEstabelecimentoId(entrega.getId(), estabelecimento.getId()))
                .thenReturn(Optional.of(entrega));
        when(buscador.usuarioComPerfil(estabelecimento.getId(), operador.getId(),
                PerfilUsuario.OPERADOR, PerfilUsuario.ADMIN_RESTAURANTE)).thenReturn(operador);
    }

    @Test
    @DisplayName("Deve ajustar o repasse da entrega e devolver a trilha registrada (UC03 A1)")
    void deveAjustar() {
        entregaEOperadorEncontrados();
        when(ajusteRepasse.ajustar(entrega, operador, reais("12.00"), "Divergência")).thenReturn(ajuste());

        EntregaAjusteValorResponse resposta = service.ajustar(estabelecimento.getId(), entrega.getId(),
                operador.getId(), new AjustarRepasseRequest(reais("12.00"), "Divergência"));

        assertEquals(reais("9.00"), resposta.valorAnterior());
        assertEquals(reais("12.00"), resposta.valorNovo());
        assertEquals(operador.getId(), resposta.usuarioId());
    }

    @Test
    @DisplayName("Deve propagar a recusa de ajuste de entrega vinculada a recibo (DVP 2.9)")
    void deveRecusarEntregaEmRecibo() {
        entregaEOperadorEncontrados();
        when(ajusteRepasse.ajustar(any(), any(), any(), any()))
                .thenThrow(new AjusteValorNaoPermitidoException("a entrega está vinculada ao recibo"));

        assertThrows(AjusteValorNaoPermitidoException.class, () -> service.ajustar(estabelecimento.getId(),
                entrega.getId(), operador.getId(), new AjustarRepasseRequest(reais("12.00"), "motivo")));
    }

    @Test
    @DisplayName("Deve recusar entrega de outra loja e usuário sem perfil de operação")
    void deveValidarTenantEPerfil() {
        UUID outra = UUID.randomUUID();
        when(entregaRepository.findByIdAndEstabelecimentoId(entrega.getId(), outra)).thenReturn(Optional.empty());
        assertThrows(EntidadeNaoEncontradaException.class, () -> service.ajustar(outra, entrega.getId(),
                operador.getId(), new AjustarRepasseRequest(reais("12.00"), "motivo")));

        when(entregaRepository.findByIdAndEstabelecimentoId(entrega.getId(), estabelecimento.getId()))
                .thenReturn(Optional.of(entrega));
        Usuario entregador = Fixtures.usuario(estabelecimento, PerfilUsuario.ENTREGADOR);
        when(buscador.usuarioComPerfil(estabelecimento.getId(), entregador.getId(),
                PerfilUsuario.OPERADOR, PerfilUsuario.ADMIN_RESTAURANTE))
                .thenThrow(new AcessoNegadoException("sem permissão"));
        assertThrows(AcessoNegadoException.class, () -> service.ajustar(estabelecimento.getId(), entrega.getId(),
                entregador.getId(), new AjustarRepasseRequest(reais("12.00"), "motivo")));
        verifyNoInteractions(ajusteRepasse);
    }

    @Test
    @DisplayName("Deve listar os ajustes da entrega filtrando pela loja")
    void deveListar() {
        when(repository.findByEntregaEstabelecimentoIdAndEntregaIdOrderByAjustadoEmAsc(
                estabelecimento.getId(), entrega.getId())).thenReturn(List.of(ajuste()));

        assertEquals(1, service.listarPorEntrega(estabelecimento.getId(), entrega.getId()).size());
    }
}
