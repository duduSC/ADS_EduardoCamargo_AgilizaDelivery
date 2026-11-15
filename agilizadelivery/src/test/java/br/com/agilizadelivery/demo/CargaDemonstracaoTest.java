package br.com.agilizadelivery.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.enums.StatusCadastroMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusCaixa;
import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import br.com.agilizadelivery.repository.CaixaRepository;
import br.com.agilizadelivery.repository.DistanciaPrecoRepository;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.EstabelecimentoRepository;
import br.com.agilizadelivery.repository.MotoboyRepository;
import br.com.agilizadelivery.repository.UsuarioRepository;

/**
 * Carga de demonstracao (T010, D009). Banco H2 proprio, para os dados
 * semeados nao se misturarem com os dos outros testes.
 */
@SpringBootTest(properties = {
        "agiliza.demo.carga-inicial=true",
        "agiliza.maps.api-key=",
        "spring.datasource.url=jdbc:h2:mem:demo-t010;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class CargaDemonstracaoTest {

    @Autowired
    private CargaDemonstracao carga;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EstabelecimentoRepository estabelecimentoRepository;

    @Autowired
    private MotoboyRepository motoboyRepository;

    @Autowired
    private DistanciaPrecoRepository distanciaPrecoRepository;

    @Autowired
    private CaixaRepository caixaRepository;

    @Autowired
    private EntregaRepository entregaRepository;

    @Value("${agiliza.demo.senha}")
    private String senha;

    @Test
    @DisplayName("A carga cria loja, usuarios, entregadores, faixas, caixa e entregas")
    void criaOsDados() {
        Estabelecimento loja = loja();
        assertThat(loja.getStatus()).isEqualTo(StatusEstabelecimento.ATIVO);
        assertThat(loja.getMaxPedidosPorLote()).isEqualTo((short) 5);
        assertThat(loja.getValorRepasseForaDeArea()).isNotNull();

        assertThat(usuarioRepository.findByEstabelecimentoIsNull()).hasSize(1);
        assertThat(usuarioRepository.findByEstabelecimentoId(loja.getId())).hasSize(4);

        List<Motoboy> entregadores = motoboyRepository.findByUsuarioEstabelecimentoId(loja.getId());
        assertThat(entregadores).hasSize(2).allSatisfy(motoboy -> {
            assertThat(motoboy.getStatusCadastro()).isEqualTo(StatusCadastroMotoboy.ATIVO);
            assertThat(motoboy.getStatusDisponibilidade()).isEqualTo(StatusDisponibilidadeMotoboy.OFFLINE);
            assertThat(motoboy.getCpfCriptografado()).doesNotContain("52998224725", "11144477735");
        });
        usuarioRepository.findAll().forEach(usuario -> assertThat(usuario.getSenhaHash()).startsWith("$2"));

        assertThat(distanciaPrecoRepository.findByEstabelecimentoIdAndAtivoTrueOrderByDeKmAsc(loja.getId()))
                .hasSize(3)
                .first()
                .satisfies(faixa -> assertThat(faixa.getDeKm()).isEqualByComparingTo(BigDecimal.ZERO));

        assertThat(caixaRepository.findByEstabelecimentoIdAndDataReferencia(loja.getId(), LocalDate.now()))
                .get()
                .satisfies(caixa -> assertThat(caixa.getStatus()).isEqualTo(StatusCaixa.ABERTO));
    }

    @Test
    @DisplayName("Sem chave do Google, as entregas recebem distancia e repasse e nao ficam em revisao")
    void entregasComDistanciaSemChave() {
        List<Entrega> entregas = entregaRepository.findByEstabelecimentoIdAndStatusOrderByCriadoEmAsc(
                loja().getId(), StatusEntrega.AGUARDANDO_DESPACHO);
        assertThat(entregas).hasSize(3);
        assertThat(entregas).extracting(Entrega::getFormaPagamento).doesNotHaveDuplicates();
        assertThat(entregas).allSatisfy(entrega -> {
            assertThat(entrega.getPendenteRevisao()).isFalse();
            assertThat(entrega.getMotivoRevisao()).isNull();
            assertThat(entrega.getDistanciaKm()).isNotNull();
            assertThat(entrega.getValorRepasse()).isNotNull();
        });
        assertThat(entregas).extracting(Entrega::getValorRepasse)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("9.00"), new BigDecimal("12.00"), new BigDecimal("15.00"));
    }

    @Test
    @DisplayName("Rodar a carga de novo nao duplica nada")
    void idempotente() {
        UUID lojaId = loja().getId();
        long usuarios = usuarioRepository.count();
        long lojas = estabelecimentoRepository.count();
        long motoboys = motoboyRepository.count();
        long faixas = distanciaPrecoRepository.count();
        long caixas = caixaRepository.count();
        long entregas = entregaRepository.count();

        carga.executar();
        carga.executar();

        assertThat(usuarioRepository.count()).isEqualTo(usuarios);
        assertThat(estabelecimentoRepository.count()).isEqualTo(lojas);
        assertThat(motoboyRepository.count()).isEqualTo(motoboys);
        assertThat(distanciaPrecoRepository.count()).isEqualTo(faixas);
        assertThat(caixaRepository.count()).isEqualTo(caixas);
        assertThat(entregaRepository.count()).isEqualTo(entregas);
        assertThat(loja().getId()).isEqualTo(lojaId);
    }

    @ParameterizedTest(name = "{0} entra pelo canal {1} como {2}")
    @CsvSource({
            "admin@agiliza.dev, BACKOFFICE, ADMIN_GERAL",
            "loja@agiliza.dev, BACKOFFICE, ADMIN_RESTAURANTE",
            "operador@agiliza.dev, BACKOFFICE, OPERADOR",
            "entregador1@agiliza.dev, APP, ENTREGADOR",
            "entregador2@agiliza.dev, APP, ENTREGADOR"
    })
    @DisplayName("O login de cada perfil semeado funciona")
    void loginDeCadaPerfil(String email, String canal, String perfil) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginEmail":"%s","senha":"%s","canal":"%s"}
                                """.formatted(email, senha, canal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.usuario.perfil", is(perfil)));
    }

    private Estabelecimento loja() {
        return estabelecimentoRepository.findByCnpj(CargaDemonstracao.CNPJ_LOJA).orElseThrow();
    }
}
