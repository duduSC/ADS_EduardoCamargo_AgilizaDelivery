package br.com.agilizadelivery.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import br.com.agilizadelivery.entity.model.DistanciaPreco;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.ItemPedido;
import br.com.agilizadelivery.entity.model.LoteEntrega;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.PedidoExterno;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusLote;
import br.com.agilizadelivery.repository.DistanciaPrecoRepository;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.LoteEntregaRepository;
import br.com.agilizadelivery.repository.PedidoExternoRepository;
import br.com.agilizadelivery.tempo.HorarioLocal;
import jakarta.persistence.EntityManagerFactory;

/** T014, D: o numero de consultas nao cresce com as linhas (RNF03) e a V2 traz os indices. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:consultas;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.generate_statistics=true" })
@AutoConfigureMockMvc
class DesempenhoConsultasTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CenarioWeb cenario;
    @Autowired
    private EntityManagerFactory fabrica;
    @Autowired
    private EntregaRepository entregaRepository;
    @Autowired
    private LoteEntregaRepository loteRepository;
    @Autowired
    private DistanciaPrecoRepository faixaRepository;
    @Autowired
    private PedidoExternoRepository pedidoRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Sem N+1 nas telas de despacho, frota, acerto e relatório do dia e no polling do app")
    void semNMaisUm() throws Exception {
        Map<String, Long> comUm = consultasPorEndpoint(1);
        Map<String, Long> comSeis = consultasPorEndpoint(6);

        assertThat(comSeis).isEqualTo(comUm);
    }

    @Test
    @DisplayName("V2 cria os índices das consultas mais usadas")
    void indicesDaV2() {
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V2__indices_de_consulta.sql"))
                .execute(jdbc.getDataSource());

        List<String> indices = jdbc.queryForList(
                "select lower(index_name) from information_schema.indexes", String.class);
        assertThat(indices).contains(
                "ix_entrega_estabelecimento_finalizada",
                "ix_entrega_estabelecimento_revisao",
                "ix_lote_motoboy_status",
                "ix_recibo_estabelecimento_data");
    }

    private Map<String, Long> consultasPorEndpoint(int linhas) throws Exception {
        Estabelecimento loja = cenario.estabelecimento();
        Usuario operador = cenario.usuario(loja, PerfilUsuario.OPERADOR);
        String bearerOperador = cenario.bearer(operador);
        Motoboy motoboy = cenario.motoboy(loja);
        List<Motoboy> outros = new ArrayList<>();
        for (int i = 1; i < linhas; i++) {
            outros.add(cenario.motoboy(loja));
        }

        LoteEntrega concluido = lote(loja, motoboy, operador, StatusLote.CONCLUIDO);
        LoteEntrega ativo = lote(loja, motoboy, operador, StatusLote.EM_ROTA);
        AtomicInteger numero = new AtomicInteger();
        for (int i = 0; i < linhas; i++) {
            cenario.entrega(loja, StatusEntrega.AGUARDANDO_DESPACHO);
            lote(loja, motoboy, operador, StatusLote.DESPACHADO);

            DistanciaPreco faixa = new DistanciaPreco();
            faixa.setEstabelecimento(loja);
            faixa.setDeKm(BigDecimal.valueOf(i));
            faixa.setAteKm(BigDecimal.valueOf(i + 1L));
            faixa.setValorPago(BigDecimal.TEN);
            faixa.setVigenteDesde(HorarioLocal.hoje());
            faixa.setAtivo(true);
            faixaRepository.save(faixa);

            Entrega finalizada = cenario.entrega(loja, StatusEntrega.ENTREGUE);
            finalizada.setLote(concluido);
            finalizada.setFaixaPreco(faixa);
            finalizada.setFinalizadaEm(HorarioLocal.hoje().atTime(12, 0));
            entregaRepository.save(finalizada);

            PedidoExterno pedido = new PedidoExterno();
            pedido.setEstabelecimento(loja);
            pedido.setProvedor("SIMULADOR");
            pedido.setIdExterno("consultas-" + UUID.randomUUID());
            pedido.setNomeCliente("Cliente");
            pedido.setValorTotal(BigDecimal.TEN);
            pedido.setPayloadJson("{}");
            ItemPedido item = new ItemPedido();
            item.setDescricao("Item " + numero.incrementAndGet());
            item.setQuantidade((short) 1);
            pedido.adicionarItem(item);
            pedidoRepository.save(pedido);

            Entrega emRota = cenario.entrega(loja, StatusEntrega.EM_ROTA);
            emRota.setLote(ativo);
            emRota.setPedidoExterno(pedido);
            emRota.setOrdemRota((short) (i + 1));
            entregaRepository.save(emRota);
        }

        // Relatorio do dia: entregas finalizadas de varios entregadores (D013)
        for (Motoboy outro : outros) {
            Entrega doOutro = cenario.entrega(loja, StatusEntrega.ENTREGUE);
            doOutro.setLote(lote(loja, outro, operador, StatusLote.CONCLUIDO));
            doOutro.setFinalizadaEm(HorarioLocal.hoje().atTime(13, 0));
            entregaRepository.save(doOutro);
        }

        String hoje = HorarioLocal.hoje().toString();
        Map<String, Long> consultas = new LinkedHashMap<>();
        consultas.put("entregas", contar("/api/entregas", bearerOperador));
        consultas.put("entregas por status", contar("/api/entregas?status=EM_ROTA", bearerOperador));
        consultas.put("lotes", contar("/api/lotes", bearerOperador));
        consultas.put("motoboys", contar("/api/motoboys", bearerOperador));
        consultas.put("motoboys online", contar("/api/motoboys?statusDisponibilidade=ONLINE", bearerOperador));
        consultas.put("motoboys ativos", contar("/api/motoboys?statusCadastro=ATIVO", bearerOperador));
        consultas.put("acertos", contar("/api/acertos?motoboyId=" + motoboy.getId()
                + "&inicio=" + hoje + "T00:00:00&fim=" + hoje + "T23:59:59", bearerOperador));
        consultas.put("acerto do app", contar("/api/entregador/acerto", cenario.bearer(motoboy.getUsuario())));
        consultas.put("lote-ativo do app",
                contar("/api/entregador/lote-ativo", cenario.bearer(motoboy.getUsuario())));
        consultas.put("relatorio do dia", contar("/api/relatorios/dia?data=" + hoje, bearerOperador));
        consultas.put("rota", contar("/api/lotes/" + ativo.getId() + "/rota", bearerOperador));
        return consultas;
    }

    private long contar(String caminho, String bearer) throws Exception {
        var estatisticas = fabrica.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();
        mockMvc.perform(MockMvcRequestBuilders.get(caminho).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
        return estatisticas.getPrepareStatementCount();
    }

    private LoteEntrega lote(Estabelecimento loja, Motoboy motoboy, Usuario operador, StatusLote status) {
        LoteEntrega lote = new LoteEntrega();
        lote.setEstabelecimento(loja);
        lote.setMotoboy(motoboy);
        lote.setUsuarioDespachante(operador);
        lote.setStatus(status);
        lote.setQtdEntregas((short) 1);
        return loteRepository.save(lote);
    }
}
