package br.com.agilizadelivery.service.support;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import br.com.agilizadelivery.exception.AcessoNegadoException;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.exception.EstabelecimentoInativoException;
import br.com.agilizadelivery.repository.EstabelecimentoRepository;
import br.com.agilizadelivery.repository.MotoboyRepository;
import br.com.agilizadelivery.repository.UsuarioRepository;
import br.com.agilizadelivery.service.Fixtures;

@ExtendWith(MockitoExtension.class)
class BuscadorTenantTest {

    @Mock
    private EstabelecimentoRepository estabelecimentoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private MotoboyRepository motoboyRepository;

    private BuscadorTenant buscador;
    private Estabelecimento estabelecimento;

    @BeforeEach
    void setUp() {
        buscador = new BuscadorTenant(estabelecimentoRepository, usuarioRepository, motoboyRepository);
        estabelecimento = Fixtures.estabelecimento();
    }

    @Test
    @DisplayName("Deve recusar operação em estabelecimento inativo (HU12)")
    void deveRecusarEstabelecimentoInativo() {
        estabelecimento.setStatus(StatusEstabelecimento.INATIVO);
        when(estabelecimentoRepository.findById(estabelecimento.getId())).thenReturn(Optional.of(estabelecimento));

        assertThrows(EstabelecimentoInativoException.class, () ->
                buscador.estabelecimentoAtivo(estabelecimento.getId()));
    }

    @Test
    @DisplayName("Deve tratar usuário e motoboy de outra loja como inexistentes (HU13)")
    void deveIsolarRecursosDeOutraLoja() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findByIdAndEstabelecimentoId(id, estabelecimento.getId())).thenReturn(Optional.empty());
        when(motoboyRepository.findByIdAndUsuarioEstabelecimentoId(id, estabelecimento.getId()))
                .thenReturn(Optional.empty());

        assertThrows(EntidadeNaoEncontradaException.class, () -> buscador.usuario(estabelecimento.getId(), id));
        assertThrows(EntidadeNaoEncontradaException.class, () -> buscador.motoboy(estabelecimento.getId(), id));
    }

    @Test
    @DisplayName("Deve exigir usuário ativo com perfil autorizado")
    void deveValidarPerfil() {
        Usuario operador = Fixtures.usuario(estabelecimento, PerfilUsuario.OPERADOR);
        when(usuarioRepository.findByIdAndEstabelecimentoId(operador.getId(), estabelecimento.getId()))
                .thenReturn(Optional.of(operador));

        assertSame(operador, buscador.usuarioComPerfil(
                estabelecimento.getId(), operador.getId(), PerfilUsuario.OPERADOR, PerfilUsuario.ADMIN_RESTAURANTE));
        assertThrows(AcessoNegadoException.class, () -> buscador.usuarioComPerfil(
                estabelecimento.getId(), operador.getId(), PerfilUsuario.ADMIN_RESTAURANTE));

        operador.setAtivo(false);
        assertThrows(AcessoNegadoException.class, () -> buscador.usuarioComPerfil(
                estabelecimento.getId(), operador.getId(), PerfilUsuario.OPERADOR));
    }
}
