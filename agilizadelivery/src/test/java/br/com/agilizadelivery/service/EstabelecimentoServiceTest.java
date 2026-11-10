package br.com.agilizadelivery.service;

import static br.com.agilizadelivery.service.Fixtures.reais;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

import br.com.agilizadelivery.dto.CoordenadaDto;
import br.com.agilizadelivery.dto.request.AdministradorInicialRequest;
import br.com.agilizadelivery.dto.request.CriarEstabelecimentoRequest;
import br.com.agilizadelivery.dto.response.EstabelecimentoResponse;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import br.com.agilizadelivery.exception.CnpjJaCadastradoException;
import br.com.agilizadelivery.exception.EmailJaCadastradoException;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.repository.EstabelecimentoRepository;
import br.com.agilizadelivery.service.impl.EstabelecimentoServiceImpl;
import br.com.agilizadelivery.service.support.CadastroUsuario;

@ExtendWith(MockitoExtension.class)
class EstabelecimentoServiceTest {

    @Mock
    private EstabelecimentoRepository repository;

    @Mock
    private CadastroUsuario cadastroUsuario;

    private EstabelecimentoService service;

    @BeforeEach
    void setUp() {
        service = new EstabelecimentoServiceImpl(repository, cadastroUsuario);
    }

    private CriarEstabelecimentoRequest requisicao() {
        return new CriarEstabelecimentoRequest("Forno Nobre Ltda", "Pizzaria Forno Nobre", "11222333000181",
                "1133334444", "Rua das Flores, 100", new CoordenadaDto(reais("-23.55"), reais("-46.63")),
                (short) 5, reais("15.00"),
                new AdministradorInicialRequest("Carla", "carla@forno.com", "senha-segura"));
    }

    @Test
    @DisplayName("Deve cadastrar o restaurante ativo e o seu usuário master (UC06, HU11)")
    void deveCriar() {
        when(repository.existsByCnpj("11222333000181")).thenReturn(false);
        when(repository.save(any(Estabelecimento.class))).then(returnsFirstArg());

        EstabelecimentoResponse criado = service.criar(requisicao());

        verify(cadastroUsuario).criar(any(Estabelecimento.class), eq("Carla"), eq("carla@forno.com"),
                eq("senha-segura"), eq(PerfilUsuario.ADMIN_RESTAURANTE));

        assertEquals(StatusEstabelecimento.ATIVO, criado.status());
        assertEquals((short) 5, criado.maxPedidosPorLote());
        assertEquals(reais("15.00"), criado.valorRepasseForaDeArea());
        assertEquals(reais("-23.55"), criado.coordenada().latitude());
    }

    @Test
    @DisplayName("Deve propagar e-mail do master já cadastrado, desfazendo o cadastro na transação")
    void deveRecusarEmailDoMasterDuplicado() {
        when(repository.existsByCnpj("11222333000181")).thenReturn(false);
        when(repository.save(any(Estabelecimento.class))).then(returnsFirstArg());
        when(cadastroUsuario.criar(any(), any(), any(), any(), any()))
                .thenThrow(new EmailJaCadastradoException("carla@forno.com"));

        assertThrows(EmailJaCadastradoException.class, () -> service.criar(requisicao()));
    }

    @Test
    @DisplayName("Deve recusar CNPJ já cadastrado (UC06 E1)")
    void deveRecusarCnpjDuplicado() {
        when(repository.existsByCnpj("11222333000181")).thenReturn(true);

        assertThrows(CnpjJaCadastradoException.class, () -> service.criar(requisicao()));
        verify(repository, never()).save(any());
        verifyNoInteractions(cadastroUsuario);
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
