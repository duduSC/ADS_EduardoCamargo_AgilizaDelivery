package br.com.agilizadelivery.integracao.pedidos;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import br.com.agilizadelivery.entity.model.embeddable.Coordenada;

/**
 * Enderecos reais de Passo Fundo/RS usados pelo simulador (T015, D013 item 6).
 *
 * Logradouro, bairro e CEP vem da base publica dos Correios, consultada pelo
 * ViaCEP (viacep.com.br) em 23/11/2026; o numero de cada endereco foi escolhido
 * dentro da faixa (e do lado par/impar) que o CEP cobre. Os bairros sao o
 * Centro, onde fica a loja da demonstracao (Av. Brasil Centro, 500), e seis
 * bairros proximos; o centro de cada um fica a ate 2,7 km da loja, em linha reta.
 *
 * A lista e fixa no codigo: nada e consultado em tempo de execucao.
 *
 * As coordenadas abaixo NAO sao do endereco: sao o centro de cada bairro no
 * OpenStreetMap (relacoes de bairro, via Nominatim, 23/11/2026, dados (c)
 * colaboradores do OpenStreetMap, licenca ODbL). So valem no modo sem chave
 * do Google (D009), para a entrega nao cair em revisao.
 */
final class EnderecosPassoFundo {

    record Endereco(String logradouro, int numero, String bairro, String cep) {

        /**
         * No formato dos enderecos brasileiros do Google, com o CEP: e o
         * texto que o backend geocodifica quando ha chave (DVP 1.5.6, passo 5).
         */
        String linha() {
            return logradouro + ", " + numero + " - " + bairro + ", Passo Fundo - RS, " + cep;
        }
    }

    static final List<Endereco> ENDERECOS = List.of(
            new Endereco("Avenida Brasil", 615, "Centro", "99010-000"),
            new Endereco("Avenida Brasil", 742, "Centro", "99010-001"),
            new Endereco("Avenida Brasil Oeste", 455, "Centro", "99025-001"),
            new Endereco("Avenida Brasil Oeste", 1230, "Centro", "99025-034"),
            new Endereco("Avenida Brasil Leste", 180, "Petrópolis", "99010-814"),
            new Endereco("Avenida Brasil Leste", 711, "Petrópolis", "99050-073"),
            new Endereco("Rua Morom", 980, "Centro", "99010-030"),
            new Endereco("Rua Morom", 1450, "Centro", "99010-032"),
            new Endereco("Rua Morom", 1960, "Centro", "99010-034"),
            new Endereco("Rua Independência", 433, "Centro", "99010-041"),
            new Endereco("Rua Independência", 820, "Centro", "99010-040"),
            new Endereco("Rua Paissandu", 640, "Centro", "99010-100"),
            new Endereco("Rua Paissandu", 1310, "Centro", "99010-101"),
            new Endereco("Rua Uruguai", 1150, "Centro", "99010-110"),
            new Endereco("Rua Uruguai", 1820, "Centro", "99010-111"),
            new Endereco("Rua Uruguai", 2550, "Boqueirão", "99010-112"),
            new Endereco("Avenida General Netto", 310, "Centro", "99010-021"),
            new Endereco("Avenida General Netto", 520, "Centro", "99010-022"),
            new Endereco("Rua Teixeira Soares", 885, "Centro", "99010-080"),
            new Endereco("Rua Teixeira Soares", 1405, "Centro", "99010-081"),
            new Endereco("Rua Bento Gonçalves", 760, "Centro", "99010-010"),
            new Endereco("Rua Bento Gonçalves", 1620, "Annes", "99020-090"),
            new Endereco("Rua Coronel Chicuta", 245, "Centro", "99010-050"),
            new Endereco("Rua Coronel Chicuta", 560, "Centro", "99010-051"),
            new Endereco("Rua Coronel Chicuta", 905, "Lucas Araújo", "99010-052"),
            new Endereco("Rua Capitão Eleutério", 870, "Centro", "99010-060"),
            new Endereco("Rua Capitão Eleutério", 1450, "Annes", "99020-190"),
            new Endereco("Rua Fagundes dos Reis", 540, "Centro", "99010-070"),
            new Endereco("Rua Fagundes dos Reis", 1180, "Annes", "99020-080"),
            new Endereco("Rua Quinze de Novembro", 690, "Centro", "99010-090"),
            new Endereco("Rua Quinze de Novembro", 1380, "Vila Luiza", "99010-091"),
            new Endereco("Avenida Presidente Vargas", 1250, "Vila Rodrigues", "99070-000"));

    /** Centro do bairro no OpenStreetMap (id da relacao no comentario). */
    static final Map<String, Coordenada> CENTRO_DO_BAIRRO = Map.of(
            "Centro", coordenada("-28.2573901", "-52.4079146"),          // 7963068
            "Petrópolis", coordenada("-28.2443607", "-52.3900892"),      // 7963060
            "Boqueirão", coordenada("-28.2658110", "-52.4267108"),       // 7963067
            "Annes", coordenada("-28.2506460", "-52.4117289"),           // 7963048
            "Lucas Araújo", coordenada("-28.2720944", "-52.3983808"),    // 7963054
            "Vila Luiza", coordenada("-28.2710929", "-52.4146861"),      // 7963053
            "Vila Rodrigues", coordenada("-28.2618088", "-52.3938042")); // 7963049

    private EnderecosPassoFundo() {
    }

    private static Coordenada coordenada(String latitude, String longitude) {
        return new Coordenada(new BigDecimal(latitude), new BigDecimal(longitude));
    }
}
