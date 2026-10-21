package br.com.agilizadelivery.exception;

import br.com.agilizadelivery.exception.handler.CodigoErro;

/**
 * Excecao lancada ao tentar importar um pedido externo que ja foi processado previamente,
 * garantindo o principio da idempotencia na integracao de pedidos.
 *
 * Referencia: DVP HU03 e secao 1.5.6 (Ciclo do worker, passo 2).
 */
public class PedidoJaImportadoException extends ConflitoDadosException {

    public PedidoJaImportadoException(String provedor, String idExterno) {
        super(
                String.format("O pedido '%s' do provedor '%s' já foi importado anteriormente.", idExterno, provedor),
                CodigoErro.PEDIDO_JA_IMPORTADO);
    }
}
