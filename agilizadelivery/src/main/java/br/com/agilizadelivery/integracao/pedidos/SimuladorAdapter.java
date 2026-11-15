package br.com.agilizadelivery.integracao.pedidos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import br.com.agilizadelivery.entity.model.ConfiguracaoIntegracao;
import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import br.com.agilizadelivery.entity.model.enums.FormaPagamento;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.service.support.Provedores;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Provedor sintetico de pedidos (DVP 1.5.6, risco R01, D009).
 *
 * A cada consulta gera de 0 a N pedidos ficticios (N = max-por-ciclo),
 * com cliente, endereco, itens e pagamento plausiveis e coordenada
 * aleatoria num raio em torno da loja. Para de gerar quando a loja ja
 * tem o limite de entregas AGUARDANDO_DESPACHO, para nao inundar o mapa.
 *
 * O payload devolvido imita a resposta de um provedor real e e gravado
 * intacto em PEDIDO_EXTERNO (RNF13).
 */
@Slf4j
@Component
public class SimuladorAdapter implements ProvedorDePedidos {

    private static final double KM_POR_GRAU = 111.32;
    private static final BigDecimal TAXA_ENTREGA = new BigDecimal("7.00");

    private static final String[] NOMES = {
        "Ana", "Bruno", "Camila", "Diego", "Eduarda", "Felipe", "Gabriela", "Henrique", "Isabela", "João",
        "Larissa", "Marcos", "Natália", "Otávio", "Paula", "Rafael", "Sabrina", "Tiago", "Vanessa", "William"
    };
    private static final String[] SOBRENOMES = {
        "Souza", "Oliveira", "Pereira", "Lima", "Costa", "Ferreira", "Rodrigues", "Almeida", "Carvalho", "Ribeiro",
        "Schneider", "Zanella", "Bortolini", "Dal Moro", "Rossetto"
    };
    private static final String[] RUAS = {
        "Rua Morom", "Av. Brasil Centro", "Rua Independência", "Rua Paissandu", "Rua Uruguai",
        "Av. Presidente Vargas", "Rua Teixeira Soares", "Rua Bento Gonçalves", "Rua Coronel Chicuta",
        "Rua Capitão Eleutério", "Av. Sete de Setembro", "Rua General Neto"
    };
    private static final String[] BAIRROS = {
        "Centro", "Boqueirão", "Vila Rodrigues", "São Cristóvão", "Petrópolis", "Lucas Araújo", "Vera Cruz",
        "Vila Luiza", "Annes"
    };
    private static final String[] COMPLEMENTOS = {null, null, "Apto 101", "Apto 304", "Casa 2", "Bloco B", "Fundos"};
    private static final String[] REFERENCIAS = {
        null, null, "Em frente à farmácia", "Ao lado do mercado", "Portão azul", "Perto da praça"
    };
    private static final String[] OBSERVACOES = {
        null, null, "Sem cebola", "Interfone com defeito, ligar ao chegar", "Mandar sachê de ketchup",
        "Cuidado com o cachorro"
    };
    private static final Object[][] CARDAPIO = {
        {"Pizza grande calabresa", "59.90"},
        {"Pizza média marguerita", "45.00"},
        {"Pizza grande quatro queijos", "64.90"},
        {"Pizza doce de chocolate", "42.00"},
        {"Calzone de frango", "38.50"},
        {"Porção de batata frita", "29.90"},
        {"Refrigerante 2 L", "12.00"},
        {"Suco natural 500 ml", "9.00"},
        {"Água mineral 500 ml", "4.50"}
    };

    private final EntregaRepository entregaRepository;
    private final IntegracaoPedidosProperties.Simulador parametros;
    private final ObjectMapper objectMapper;
    private final RandomGenerator aleatorio;
    private final AtomicInteger numeroExibicao = new AtomicInteger(1000);

    @Autowired
    public SimuladorAdapter(
            EntregaRepository entregaRepository,
            IntegracaoPedidosProperties properties,
            ObjectMapper objectMapper) {
        this(entregaRepository, properties, objectMapper, RandomGenerator.getDefault());
    }

    /** Construtor com o gerador aleatorio explicito, para testes reprodutiveis. */
    public SimuladorAdapter(
            EntregaRepository entregaRepository,
            IntegracaoPedidosProperties properties,
            ObjectMapper objectMapper,
            RandomGenerator aleatorio) {
        this.entregaRepository = entregaRepository;
        this.parametros = properties.simulador();
        this.objectMapper = objectMapper;
        this.aleatorio = aleatorio;
    }

    @Override
    public String provedor() {
        return Provedores.SIMULADOR;
    }

    @Override
    public List<PedidoRecebido> buscarNovosPedidos(ConfiguracaoIntegracao configuracao) {
        UUID lojaId = configuracao.getEstabelecimento().getId();
        long aguardando = entregaRepository.countByEstabelecimentoIdAndStatus(
                lojaId, StatusEntrega.AGUARDANDO_DESPACHO);
        long vagas = parametros.limiteAguardando() - aguardando;
        if (vagas <= 0) {
            log.debug("Simulador sem gerar para a loja {}: {} entregas aguardando despacho.", lojaId, aguardando);
            return List.of();
        }
        int quantidade = (int) Math.min(vagas, aleatorio.nextInt(parametros.maxPorCiclo() + 1));
        Coordenada loja = configuracao.getEstabelecimento().getCoordenada();
        List<PedidoRecebido> pedidos = new ArrayList<>(quantidade);
        for (int i = 0; i < quantidade; i++) {
            pedidos.add(gerar(configuracao.getMerchantId(), loja));
        }
        return pedidos;
    }

    /** O simulador nao exige confirmacao. */
    @Override
    public void confirmarRecebimento(ConfiguracaoIntegracao configuracao, String idExterno) {
    }

    private PedidoRecebido gerar(String merchantId, Coordenada loja) {
        String idExterno = "SIM-" + UUID.randomUUID();
        String numero = String.valueOf(numeroExibicao.incrementAndGet());
        String nome = sortear(NOMES) + " " + sortear(SOBRENOMES);
        String telefone = "549" + (91000000 + aleatorio.nextInt(9000000));
        String bairro = sortear(BAIRROS);
        String endereco = sortear(RUAS) + ", " + (10 + aleatorio.nextInt(1990)) + " - " + bairro + ", Passo Fundo/RS";
        String cep = "990" + (10 + aleatorio.nextInt(89)) + "-" + String.format("%03d", aleatorio.nextInt(1000));
        String complemento = sortear(COMPLEMENTOS);
        String referencia = sortear(REFERENCIAS);
        String observacoes = sortear(OBSERVACOES);
        Coordenada coordenada = pontoProximo(loja);

        List<PedidoRecebido.Item> itens = new ArrayList<>();
        int qtdItens = 1 + aleatorio.nextInt(4);
        for (int i = 0; i < qtdItens; i++) {
            Object[] prato = CARDAPIO[aleatorio.nextInt(CARDAPIO.length)];
            itens.add(new PedidoRecebido.Item(
                    (String) prato[0], (short) (1 + aleatorio.nextInt(2)), new BigDecimal((String) prato[1]), null));
        }
        BigDecimal valorItens = itens.stream()
                .map(item -> item.valorUnitario().multiply(BigDecimal.valueOf(item.quantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal valorTotal = valorItens.add(TAXA_ENTREGA);

        FormaPagamento forma = FormaPagamento.values()[aleatorio.nextInt(FormaPagamento.values().length)];
        boolean pagoOnline = (forma == FormaPagamento.PIX || forma == FormaPagamento.CREDITO) && aleatorio.nextBoolean();
        BigDecimal trocoPara = forma == FormaPagamento.DINHEIRO && aleatorio.nextBoolean()
                ? trocoAcimaDe(valorTotal)
                : null;

        String payload = payload(idExterno, numero, merchantId, nome, telefone, endereco, bairro, cep, complemento,
                referencia, coordenada, itens, valorItens, valorTotal, forma, pagoOnline, trocoPara, observacoes);
        return new PedidoRecebido(idExterno, numero, "PLACED", nome, telefone, endereco, bairro, cep, complemento,
                referencia, coordenada, valorItens, TAXA_ENTREGA, valorTotal, forma, pagoOnline, trocoPara,
                observacoes, itens, payload);
    }

    /** Ponto uniforme num disco de raio raioKm em torno da loja. */
    private Coordenada pontoProximo(Coordenada loja) {
        double distancia = parametros.raioKm() * Math.sqrt(aleatorio.nextDouble());
        double angulo = 2 * Math.PI * aleatorio.nextDouble();
        double latitude = loja.getLatitude().doubleValue();
        double dLat = distancia * Math.cos(angulo) / KM_POR_GRAU;
        double dLng = distancia * Math.sin(angulo) / (KM_POR_GRAU * Math.cos(Math.toRadians(latitude)));
        return new Coordenada(
                BigDecimal.valueOf(latitude + dLat).setScale(7, RoundingMode.HALF_UP),
                BigDecimal.valueOf(loja.getLongitude().doubleValue() + dLng).setScale(7, RoundingMode.HALF_UP));
    }

    /** Proxima nota "redonda" (multiplo de R$ 50) acima do total. */
    private static BigDecimal trocoAcimaDe(BigDecimal total) {
        BigDecimal cinquenta = new BigDecimal("50");
        return total.divide(cinquenta, 0, RoundingMode.FLOOR).add(BigDecimal.ONE).multiply(cinquenta)
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    private String payload(String idExterno, String numero, String merchantId, String nome, String telefone,
            String endereco, String bairro, String cep, String complemento, String referencia, Coordenada coordenada,
            List<PedidoRecebido.Item> itens, BigDecimal valorItens, BigDecimal valorTotal, FormaPagamento forma,
            boolean pagoOnline, BigDecimal trocoPara, String observacoes) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("id", idExterno);
        corpo.put("displayId", numero);
        corpo.put("merchantId", merchantId);
        corpo.put("status", "PLACED");
        corpo.put("customer", Map.of("name", nome, "phone", telefone));
        Map<String, Object> entrega = new LinkedHashMap<>();
        entrega.put("streetAddress", endereco);
        entrega.put("neighborhood", bairro);
        entrega.put("postalCode", cep);
        entrega.put("complement", complemento);
        entrega.put("reference", referencia);
        entrega.put("latitude", coordenada.getLatitude());
        entrega.put("longitude", coordenada.getLongitude());
        corpo.put("deliveryAddress", entrega);
        corpo.put("items", itens.stream()
                .map(item -> Map.of("name", item.descricao(), "quantity", item.quantidade(),
                        "unitPrice", item.valorUnitario()))
                .toList());
        Map<String, Object> total = new LinkedHashMap<>();
        total.put("subTotal", valorItens);
        total.put("deliveryFee", TAXA_ENTREGA);
        total.put("orderAmount", valorTotal);
        corpo.put("total", total);
        Map<String, Object> pagamento = new LinkedHashMap<>();
        pagamento.put("method", forma.name());
        pagamento.put("prepaid", pagoOnline);
        pagamento.put("changeFor", trocoPara);
        corpo.put("payment", pagamento);
        corpo.put("observations", observacoes);
        return objectMapper.writeValueAsString(corpo);
    }

    private String sortear(String[] opcoes) {
        return opcoes[aleatorio.nextInt(opcoes.length)];
    }
}
