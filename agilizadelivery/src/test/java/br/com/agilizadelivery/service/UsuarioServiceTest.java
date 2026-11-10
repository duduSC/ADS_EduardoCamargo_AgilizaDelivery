package br.com.agilizadelivery.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.agilizadelivery.dto.request.CriarUsuarioRequest;
import br.com.agilizadelivery.dto.response.UsuarioResponse;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.repository.UsuarioRepository;
import br.com.agilizadelivery.service.impl.UsuarioServiceImpl;
import br.com.agilizadelivery.service.support.BuscadorTenant;
import br.com.agilizadelivery.service.support.CadastroUsuario;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository repository;

    @Mock
    private BuscadorTenant buscador;

    @Mock
    private CadastroUsuario cadastroUsuario;

    private UsuarioService service;
    private Estabelecimento estabelecimento;

    @BeforeEach
    void setUp() {
        service = new UsuarioServiceImpl(repository, buscador, cadastroUsuario);
        estabelecimento = Fixtures.estabelecimento();
    }

    @Test
    @DisplayName("Deve criar operador na loja ativa")
    void deveCriarUsuario() {
        Usuario operador = Fixtures.usuario(estabelecimento, PerfilUsuario.OPERADOR);
        when(buscador.estabelecimentoAtivo(estabelecimento.getId())).thenReturn(estabelecimento);
        when(cadastroUsuario.criar(estabelecimento, "Ana", "ana@forno.com", "senha-segura", PerfilUsuario.OPERADOR))
                .thenReturn(operador);

        UsuarioResponse criado = service.criar(estabelecimento.getId(),
                new CriarUsuarioRequest("Ana", "ana@forno.com", "senha-segura", PerfilUsuario.OPERADOR));

        assertEquals(operador.getId(), criado.id());
        assertEquals(estabelecimento.getId(), criado.estabelecimentoId());
    }

    @ParameterizedTest
    @EnumSource(value = PerfilUsuario.class, names = {"ADMIN_GERAL", "ENTREGADOR"})
    @DisplayName("Deve recusar perfis que não pertencem a esta operação")
    void deveRecusarPerfil(PerfilUsuario perfil) {
        assertThrows(RegraDeNegocioException.class, () -> service.criar(estabelecimento.getId(),
                new CriarUsuarioRequest("X", "x@x.com", "senha-segura", perfil)));
        verifyNoInteractions(cadastroUsuario);
    }

    @Test
    @DisplayName("Deve consultar, listar e desativar usuários da loja")
    void deveConsultarEAlterarAtivo() {
        Usuario operador = Fixtures.usuario(estabelecimento, PerfilUsuario.OPERADOR);
        when(buscador.usuario(estabelecimento.getId(), operador.getId())).thenReturn(operador);
        when(repository.findByEstabelecimentoId(estabelecimento.getId())).thenReturn(List.of(operador));
        when(repository.findByEstabelecimentoIdAndPerfil(estabelecimento.getId(), PerfilUsuario.OPERADOR))
                .thenReturn(List.of(operador));
        when(repository.save(operador)).thenReturn(operador);

        assertEquals(operador.getId(), service.buscarPorId(estabelecimento.getId(), operador.getId()).id());
        assertEquals(1, service.listarPorEstabelecimento(estabelecimento.getId()).size());
        assertEquals(1, service.listarPorEstabelecimentoEPerfil(estabelecimento.getId(), PerfilUsuario.OPERADOR).size());
        assertFalse(service.alterarAtivo(estabelecimento.getId(), operador.getId(), false).ativo());
    }
}
