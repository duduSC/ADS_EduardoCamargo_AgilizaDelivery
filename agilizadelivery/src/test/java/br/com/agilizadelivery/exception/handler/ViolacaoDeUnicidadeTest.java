package br.com.agilizadelivery.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.math.BigDecimal;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.controller.CenarioWeb;
import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.PedidoExterno;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.repository.CaixaRepository;
import br.com.agilizadelivery.repository.EstabelecimentoRepository;
import br.com.agilizadelivery.repository.MotoboyRepository;
import br.com.agilizadelivery.repository.PedidoExternoRepository;
import br.com.agilizadelivery.repository.UsuarioRepository;
import br.com.agilizadelivery.tempo.HorarioLocal;

/**
 * Quando duas requisicoes passam juntas pela checagem de duplicidade, quem
 * barra e a constraint do banco: o codigo tem de ser o mesmo da checagem.
 */
@SpringBootTest
@Transactional
class ViolacaoDeUnicidadeTest {

    @Autowired
    private CenarioWeb cenario;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EstabelecimentoRepository estabelecimentoRepository;

    @Autowired
    private MotoboyRepository motoboyRepository;

    @Autowired
    private CaixaRepository caixaRepository;

    @Autowired
    private PedidoExternoRepository pedidoRepository;

    private Estabelecimento loja;

    @BeforeEach
    void setUp() {
        loja = cenario.estabelecimento();
    }

    @Test
    @DisplayName("E-mail repetido no banco: EMAIL_JA_CADASTRADO")
    void email() {
        Usuario existente = cenario.usuario(loja, PerfilUsuario.OPERADOR);
        Usuario novo = new Usuario();
        novo.setEstabelecimento(loja);
        novo.setNome("Outro");
        novo.setLoginEmail(existente.getLoginEmail());
        novo.setSenhaHash(existente.getSenhaHash());
        novo.setPerfil(PerfilUsuario.OPERADOR);

        exigirCodigo(() -> usuarioRepository.saveAndFlush(novo), CodigoErro.EMAIL_JA_CADASTRADO);
    }

    @Test
    @DisplayName("CNPJ repetido no banco: CNPJ_JA_CADASTRADO")
    void cnpj() {
        Estabelecimento nova = new Estabelecimento();
        nova.setRazaoSocial("Outra Ltda");
        nova.setNomeFantasia("Outra");
        nova.setCnpj(loja.getCnpj());
        nova.setEnderecoCompleto("Rua B, 2");
        nova.setCoordenada(loja.getCoordenada());

        exigirCodigo(() -> estabelecimentoRepository.saveAndFlush(nova), CodigoErro.CNPJ_JA_CADASTRADO);
    }

    @Test
    @DisplayName("CPF repetido no banco: CPF_JA_CADASTRADO")
    void cpf() {
        Motoboy existente = cenario.motoboy(loja);
        Motoboy novo = new Motoboy();
        novo.setUsuario(cenario.usuario(loja, PerfilUsuario.ENTREGADOR));
        novo.setCpfCriptografado("cifrado");
        novo.setCpfHash(existente.getCpfHash());
        novo.setTelefone("11999990000");

        exigirCodigo(() -> motoboyRepository.saveAndFlush(novo), CodigoErro.CPF_JA_CADASTRADO);
    }

    @Test
    @DisplayName("Dois caixas no dia: CAIXA_JA_ABERTO")
    void caixa() {
        cenario.caixa(loja);
        Caixa novo = new Caixa();
        novo.setEstabelecimento(loja);
        novo.setDataReferencia(HorarioLocal.hoje());
        novo.setAbertoEm(HorarioLocal.agora());

        exigirCodigo(() -> caixaRepository.saveAndFlush(novo), CodigoErro.CAIXA_JA_ABERTO);
    }

    @Test
    @DisplayName("Pedido repetido no banco: PEDIDO_JA_IMPORTADO")
    void pedido() {
        pedidoRepository.saveAndFlush(pedidoExterno());

        exigirCodigo(() -> pedidoRepository.saveAndFlush(pedidoExterno()), CodigoErro.PEDIDO_JA_IMPORTADO);
    }

    private PedidoExterno pedidoExterno() {
        PedidoExterno pedido = new PedidoExterno();
        pedido.setEstabelecimento(loja);
        pedido.setProvedor("SIMULADOR");
        pedido.setIdExterno("unicidade-" + loja.getId());
        pedido.setNomeCliente("Cliente");
        pedido.setValorTotal(BigDecimal.TEN);
        pedido.setPayloadJson("{}");
        return pedido;
    }

    private static void exigirCodigo(ThrowingCallable operacao, CodigoErro esperado) {
        DataIntegrityViolationException violacao =
                catchThrowableOfType(DataIntegrityViolationException.class, operacao);
        assertThat(violacao).isNotNull();

        ResponseEntity<ApiErrorResponse> resposta =
                new GlobalExceptionHandler().tratarIntegridadeBanco(violacao, new MockHttpServletRequest());

        assertThat(resposta.getStatusCode().value()).isEqualTo(409);
        assertThat(resposta.getBody().getCodigo()).isEqualTo(esperado);
    }
}
