package br.com.agilizadelivery.integracao.pedidos;

import java.util.List;

import br.com.agilizadelivery.entity.model.ConfiguracaoIntegracao;

/**
 * Porta de integracao com um provedor de pedidos (DVP 1.5.6).
 *
 * Implementacoes: SimuladorAdapter (pedidos sinteticos) e, em tarefa
 * futura, IFoodAdapter. O worker so conhece esta interface e o contrato
 * neutro PedidoRecebido.
 */
public interface ProvedorDePedidos {

    /** Valor de provedor atendido, como em service.support.Provedores. */
    String provedor();

    /** Pedidos novos da loja identificada por configuracao.merchantId (passo 1). */
    List<PedidoRecebido> buscarNovosPedidos(ConfiguracaoIntegracao configuracao);

    /** Confirma ao provedor o recebimento do pedido, quando o protocolo exigir (passo 4). */
    void confirmarRecebimento(ConfiguracaoIntegracao configuracao, String idExterno);
}
