package br.com.agilizadelivery.service.support;

import java.util.Locale;
import java.util.Set;

import br.com.agilizadelivery.exception.ProvedorDesconhecidoException;

/**
 * Provedores de pedidos aceitos pela aplicacao (DVP 1.5.4 e 1.5.6).
 *
 * Desde o DVP 3.0, a coluna provedor e VARCHAR(20) em CONFIGURACAO_INTEGRACAO
 * e em PEDIDO_EXTERNO, e o conjunto de valores aceitos e validado aqui, nao
 * por um tipo do banco. Integrar um provedor novo e acrescentar a constante,
 * junto com o adapter correspondente de ProvedorDePedidos.
 *
 * Todo valor passa por {@link #normalizar(String)} antes de gravar ou
 * consultar, para a chave de idempotencia (provedor, id_externo) nao
 * depender de caixa nem de espacos.
 */
public final class Provedores {

    public static final String IFOOD = "IFOOD";
    public static final String SIMULADOR = "SIMULADOR";

    private static final Set<String> ACEITOS = Set.of(IFOOD, SIMULADOR);

    private Provedores() {
    }

    /**
     * Devolve o provedor em maiusculas e sem espacos nas pontas.
     *
     * @throws ProvedorDesconhecidoException se o valor for nulo, vazio ou
     *         nao estiver entre os aceitos
     */
    public static String normalizar(String provedor) {
        String normalizado = provedor == null ? "" : provedor.strip().toUpperCase(Locale.ROOT);
        if (!ACEITOS.contains(normalizado)) {
            throw new ProvedorDesconhecidoException(provedor, ACEITOS);
        }
        return normalizado;
    }

    public static Set<String> aceitos() {
        return ACEITOS;
    }
}
