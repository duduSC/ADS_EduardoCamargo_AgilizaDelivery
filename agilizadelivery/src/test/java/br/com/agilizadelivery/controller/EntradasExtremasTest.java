package br.com.agilizadelivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.jayway.jsonpath.JsonPath;

import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;

/**
 * Payloads extremos em cada request (T014, C): sempre 4xx de entrada, nunca
 * 500, nunca "duplicado" por estouro de coluna.
 *
 * Sem @Transactional de proposito: boa parte so falha no commit.
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:extremos;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class EntradasExtremasTest {

    private enum Quem {
        OPERADOR, ADMIN, PLATAFORMA, ENTREGADOR
    }

    private record Caso(String nome, Quem quem, String metodo, String caminho, String corpo, int esperado,
            String codigo) {

        @Override
        public String toString() {
            return nome;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CenarioWeb cenario;

    private Motoboy motoboy;
    private Entrega entrega;
    private Entrega finalizada;
    private String operador;
    private String admin;
    private String plataforma;
    private String entregador;

    @BeforeEach
    void setUp() {
        Estabelecimento loja = cenario.estabelecimento();
        operador = cenario.bearer(cenario.usuario(loja, PerfilUsuario.OPERADOR));
        admin = cenario.bearer(cenario.usuario(loja, PerfilUsuario.ADMIN_RESTAURANTE));
        plataforma = cenario.bearer(cenario.usuario(null, PerfilUsuario.ADMIN_GERAL));
        motoboy = cenario.motoboy(loja);
        entregador = cenario.bearer(motoboy.getUsuario());
        entrega = cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);
        finalizada = cenario.entrega(loja, StatusEntrega.ENTREGUE);
        cenario.caixa(loja);
    }

    static Stream<Caso> casos() {
        String corpo = "CORPO_REQUISICAO_INVALIDO";
        String dados = "DADOS_INVALIDOS";
        String regra = "REGRA_NEGOCIO_VIOLADA";
        String avulsa = "{\"nomeCliente\":\"C\",\"enderecoCompleto\":\"Rua A\",%s}";
        String faixa = "[{\"deKm\":0,\"ateKm\":%s,\"valorPago\":%s,\"vigenteDesde\":\"2026-11-22\"}]";
        return Stream.of(
                // numero curto que vira gigante: negacao de servico com poucos bytes
                new Caso("expoente na posicao", Quem.ENTREGADOR, "PATCH", "/api/entregador/posicao",
                        "{\"latitude\":1e-1000,\"longitude\":-46.6}", 400, corpo),
                new Caso("expoente no pino", Quem.OPERADOR, "PATCH", "/api/entregas/{entrega}/coordenada",
                        "{\"latitude\":1e-1000,\"longitude\":-46.6}", 400, corpo),
                new Caso("expoente na entrega avulsa", Quem.OPERADOR, "POST", "/api/entregas",
                        avulsa.formatted("\"coordenada\":{\"latitude\":-23.5,\"longitude\":1e-1000}"), 400, corpo),
                new Caso("expoente em texto", Quem.ENTREGADOR, "PATCH", "/api/entregador/posicao",
                        "{\"latitude\":\"1e-1000\",\"longitude\":-46.6}", 400, corpo),
                new Caso("expoente positivo no valor", Quem.OPERADOR, "POST", "/api/entregas",
                        avulsa.formatted("\"valorPedido\":1e5000"), 400, corpo),

                // DECIMAL(10,2) e DECIMAL(5,2)
                new Caso("valorPedido acima da coluna", Quem.OPERADOR, "POST", "/api/entregas",
                        avulsa.formatted("\"valorPedido\":100000000"), 400, dados),
                new Caso("valorPedido com milesimos", Quem.OPERADOR, "POST", "/api/entregas",
                        avulsa.formatted("\"valorPedido\":10.555"), 400, dados),
                new Caso("trocoPara acima da coluna", Quem.OPERADOR, "POST", "/api/entregas",
                        avulsa.formatted("\"trocoPara\":100000000"), 400, dados),
                new Caso("ajuste acima da coluna", Quem.OPERADOR, "POST", "/api/entregas/{finalizada}/ajustes",
                        "{\"valorNovo\":100000000,\"motivo\":\"x\"}", 400, dados),
                new Caso("ajuste com milesimos", Quem.OPERADOR, "POST", "/api/entregas/{finalizada}/ajustes",
                        "{\"valorNovo\":12.345,\"motivo\":\"x\"}", 400, dados),
                new Caso("resolucao com milesimos", Quem.OPERADOR, "POST",
                        "/api/entregas/{finalizada}/revisao/resolver",
                        "{\"valorRepasse\":12.345,\"motivo\":\"x\"}", 400, dados),
                new Caso("km acima da coluna", Quem.ADMIN, "PUT", "/api/faixas-preco",
                        faixa.formatted("1000", "9.00"), 400, dados),
                new Caso("km que arredonda para zero", Quem.ADMIN, "PUT", "/api/faixas-preco",
                        faixa.formatted("0.004", "9.00"), 400, dados),
                new Caso("repasse que arredonda para zero", Quem.ADMIN, "PUT", "/api/faixas-preco",
                        faixa.formatted("3", "0.001"), 400, dados),
                new Caso("fora de area acima da coluna", Quem.ADMIN, "PATCH", "/api/estabelecimento",
                        "{\"valorRepasseForaDeArea\":100000000}", 400, dados),
                new Caso("SMALLINT estourado", Quem.ADMIN, "PATCH", "/api/estabelecimento",
                        "{\"maxPedidosPorLote\":40000}", 400, corpo),
                new Caso("expoente em inteiro", Quem.ADMIN, "PATCH", "/api/estabelecimento",
                        "{\"maxPedidosPorLote\":1e999999999}", 400, corpo),

                // textos
                new Caso("telefone em branco na edicao", Quem.ADMIN, "PUT", "/api/motoboys/{motoboy}",
                        "{\"telefone\":\"   \"}", 400, dados),
                new Caso("placa longa", Quem.ADMIN, "PUT", "/api/motoboys/{motoboy}",
                        "{\"placaVeiculo\":\"ABC1D23456\"}", 400, dados),
                new Caso("senha com mais de 72 bytes", Quem.ADMIN, "POST", "/api/usuarios",
                        "{\"nome\":\"Ana\",\"loginEmail\":\"ana.longa@teste.com\",\"perfil\":\"OPERADOR\",\"senha\":\""
                                + "ç".repeat(50) + "\"}", 400, dados),

                // coordenada
                new Caso("posicao fora da Terra", Quem.ENTREGADOR, "PATCH", "/api/entregador/posicao",
                        "{\"latitude\":91,\"longitude\":0}", 422, regra),
                new Caso("loja fora da Terra", Quem.PLATAFORMA, "POST", "/api/estabelecimentos",
                        novaLoja("11444777000161", "{\"latitude\":95,\"longitude\":0}"), 422, regra),
                new Caso("loja sem latitude", Quem.PLATAFORMA, "POST", "/api/estabelecimentos",
                        novaLoja("11444777000161", "{\"longitude\":-46.6}"), 422, regra));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    @DisplayName("Entrada extrema é recusada como entrada inválida")
    void entradaExtrema(Caso caso) throws Exception {
        String caminho = caso.caminho()
                .replace("{entrega}", entrega.getId().toString())
                .replace("{finalizada}", finalizada.getId().toString())
                .replace("{motoboy}", motoboy.getId().toString());

        MockHttpServletResponse resposta = mockMvc.perform(MockMvcRequestBuilders
                        .request(HttpMethod.valueOf(caso.metodo()), caminho)
                        .header(HttpHeaders.AUTHORIZATION, bearer(caso.quem()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(caso.corpo()))
                .andReturn().getResponse();

        assertThat(resposta.getStatus()).as(resposta.getContentAsString()).isEqualTo(caso.esperado());
        assertThat(resposta.getContentAsString()).contains("\"codigo\":\"" + caso.codigo() + "\"");
    }

    @Test
    @DisplayName("CNPJ formatado é gravado só com dígitos e continua único (HU11)")
    void cnpjFormatado() throws Exception {
        String digitos = cnpjNovo();
        String formatado = digitos.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
        String coordenada = "{\"latitude\":-28.26,\"longitude\":-52.41}";

        String criado = criarLoja(novaLoja(formatado, coordenada))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(criado, "$.cnpj")).isEqualTo(digitos);

        criarLoja(novaLoja(digitos, coordenada))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo", is("CNPJ_JA_CADASTRADO")));
    }

    @Test
    @DisplayName("Números normais dos clientes continuam aceitos")
    void numerosNormais() throws Exception {
        // o GPS manda doubles inteiros (ate uns 20 decimais); o front, no maximo centavos
        mockMvc.perform(MockMvcRequestBuilders.patch("/api/entregador/posicao")
                        .header(HttpHeaders.AUTHORIZATION, entregador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":-28.262000000000004123,\"longitude\":1.2E-7}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/entregas")
                        .header(HttpHeaders.AUTHORIZATION, operador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nomeCliente":"C","enderecoCompleto":"Rua A","valorPedido":99999999.99,
                                 "trocoPara":100,"coordenada":{"latitude":-23.56,"longitude":-46.65}}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Login com senha enorme é 401, e não 500")
    void senhaEnormeNoLogin() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginEmail\":\"%s\",\"senha\":\"%s\"}"
                                .formatted(motoboy.getUsuario().getLoginEmail(), "ç".repeat(200))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("CREDENCIAIS_INVALIDAS")));
    }

    private ResultActions criarLoja(String corpo) throws Exception {
        return mockMvc.perform(post("/api/estabelecimentos")
                .header(HttpHeaders.AUTHORIZATION, plataforma)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private String bearer(Quem quem) {
        return switch (quem) {
            case OPERADOR -> operador;
            case ADMIN -> admin;
            case PLATAFORMA -> plataforma;
            case ENTREGADOR -> entregador;
        };
    }

    private static String novaLoja(String cnpj, String coordenada) {
        return """
                {"razaoSocial":"Loja Ltda","nomeFantasia":"Loja","cnpj":"%s","enderecoCompleto":"Rua A, 1",
                 "coordenada":%s,"maxPedidosPorLote":5,
                 "administrador":{"nome":"Dono","loginEmail":"dono.%s@teste.com","senha":"senha-segura"}}
                """.formatted(cnpj, coordenada, UUID.randomUUID().toString().substring(0, 13));
    }

    private static String cnpjNovo() {
        StringBuilder cnpj = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            cnpj.append(ThreadLocalRandom.current().nextInt(1, 10));
        }
        cnpj.append("0001");
        for (int rodada = 0; rodada < 2; rodada++) {
            int soma = 0;
            for (int i = cnpj.length() - 1, peso = 2; i >= 0; i--, peso = peso == 9 ? 2 : peso + 1) {
                soma += (cnpj.charAt(i) - '0') * peso;
            }
            cnpj.append(soma % 11 < 2 ? 0 : 11 - soma % 11);
        }
        return cnpj.toString();
    }
}
