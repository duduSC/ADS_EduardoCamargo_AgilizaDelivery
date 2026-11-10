package br.com.agilizadelivery.service;

import static br.com.agilizadelivery.service.Fixtures.reais;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

import br.com.agilizadelivery.dto.CoordenadaDto;
import br.com.agilizadelivery.dto.request.CriarEstabelecimentoRequest;
import br.com.agilizadelivery.dto.response.EstabelecimentoResponse;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import br.com.agilizadelivery.exception.CnpjJaCadastradoException;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.repository.EstabelecimentoRepository;
import br.com.agilizadelivery.service.impl.EstabelecimentoServiceImpl;

@ExtendWith(MockitoExtension.class)
class EstabelecimentoServiceTest {

    @Mock
    private EstabelecimentoRepository repository;

    private EstabelecimentoService service;

    @BeforeEach
    void setUp() {
        service = new EstabelecimentoServiceImpl(repository);
    }

    private CriarEstabelecimentoRequest requisicao() {
        return new CriarEstabelecimentoRequest("Forno Nobre Ltda", "Pizzaria Forno Nobre", "11222333000181",
                "1133334444", "Rua das Flores, 100", new CoordenadaDto(reais("-23.55"), reais("-46.63")),
                (short) 5, reais("15.00"));
    }

    @Test
    @DisplayName("Deve cadastrar o restaurante ativo com os parâmetros operacionais (HU11)")
    void deveCriar() {
        when(repository.existsByCnpj("11222333000181")).thenReturn(false);
        when(repository.save(any(Estabelecimento.class))).then(returnsFirstArg());

        EstabelecimentoResponse criado = service.criar(requisicao());

        assertEquals(StatusEstabelecimento.ATIVO, criado.status());
        assertEquals((short) 5, criado.maxPedidosPorLote());
        assertEquals(reais("15.00"), criado.valorRepasseForaDeArea());
        assertEquals(reais("-23.55"), criado.coordenada().latitude());
    }

    @Test
    @DisplayName("Deve recusar CNPJ já cadastrado (UC06 E1)")
    void deveRecusarCnpjDuplicado() {
        when(repository.existsByCnpj("11222333000181")).thenReturn(true);

        assertThrows(CnpjJaCadastradoException.class, () -> service.criar(requisicao()));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve inativar e reativar o restaurante (HU12)")
    void deveAlterarStatus() {
        Estabelecimento estabelecimento = Fixtures.estabelecimento();
        when(repository.findById(estabelecimento.getId())).thenReturn(Optional.of(estabelecimento));
        when(repository.save(estabelecimento)).thenReturn(estabelecimento);

        assertEquals(StatusEstabelecimento.INATIVO,
                service.alterarStatus(estabelecimento.getId(), StatusEstabelecimento.INATIVO).status());
        assertEquals(StatusEstabelecimento.ATIVO,
                service.alterarStatus(estabelecimento.getId(), StatusEstabelecimento.ATIVO).status());
    }

    @Test
    @DisplayName("Deve consultar por id e listar por status")
    void deveConsultar() {
        Estabelecimento estabelecimento = Fixtures.estabelecimento();
        when(repository.findById(estabelecimento.getId())).thenReturn(Optional.of(estabelecimento));
        when(repository.findByStatus(StatusEstabelecimento.ATIVO)).thenReturn(List.of(estabelecimento));

        assertEquals(estabelecimento.getId(), service.buscarPorId(estabelecimento.getId()).id());
        assertEquals(1, service.listarPorStatus(StatusEstabelecimento.ATIVO).size());
        assertThrows(EntidadeNaoEncontradaException.class, () -> service.buscarPorId(UUID.randomUUID()));
    }
}
