package br.com.agilizadelivery.service.support;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.dto.DtoMapper;
import br.com.agilizadelivery.dto.response.EntregaResponse;
import br.com.agilizadelivery.entity.model.ConfiguracaoIntegracao;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.ItemPedido;
import br.com.agilizadelivery.entity.model.PedidoExterno;
import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.integracao.pedidos.PedidoRecebido;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.PedidoExternoRepository;
import br.com.agilizadelivery.tempo.HorarioLocal;
import br.com.agilizadelivery.websocket.PublicadorTempoReal;

/**
 * Importacao de um pedido do provedor em PEDIDO_EXTERNO + ENTREGA (DVP
 * 1.5.6, passos 2, 3, 5 e 6; HU03).
 *
 * Tudo numa transacao: ou o pedido e a entrega sao gravados juntos, ou
 * nada e gravado e o worker repete (RNF04).
 */
@Component
public class ImportacaoPedido {

    private final PedidoExternoRepository pedidoRepository;
    private final EntregaRepository entregaRepository;
    private final BuscadorTenant buscador;
    private final LocalizacaoEntrega localizacao;
    private final TransicaoEntrega transicao;
    private final PublicadorTempoReal publicador;

    public ImportacaoPedido(
            PedidoExternoRepository pedidoRepository,
            EntregaRepository entregaRepository,
            BuscadorTenant buscador,
            LocalizacaoEntrega localizacao,
            TransicaoEntrega transicao,
            PublicadorTempoReal publicador) {
        this.pedidoRepository = pedidoRepository;
        this.entregaRepository = entregaRepository;
        this.buscador = buscador;
        this.localizacao = localizacao;
        this.transicao = transicao;
        this.publicador = publicador;
    }

    /**
     * Importa o pedido e cria a entrega AGUARDANDO_DESPACHO. Vazio quando o
     * par (provedor, id_externo) ja existe: o pedido e descartado (HU03).
     */
    @Transactional
    public Optional<EntregaResponse> importar(ConfiguracaoIntegracao configuracao, PedidoRecebido recebido) {
        String provedor = Provedores.normalizar(configuracao.getProvedor());
        if (pedidoRepository.existsByProvedorAndIdExterno(provedor, recebido.idExterno())) {
            return Optional.empty();
        }
        Estabelecimento loja = buscador.estabelecimentoAtivo(configuracao.getEstabelecimento().getId());

        PedidoExterno pedido = pedidoExterno(provedor, loja, recebido);
        Entrega entrega = entrega(loja, pedido, recebido);
        localizacao.localizar(entrega);
        pedido.setProcessadoEm(HorarioLocal.agora());
        pedidoRepository.save(pedido);

        Entrega salva = entregaRepository.save(entrega);
        transicao.registrarCriacao(salva, null);
        EntregaResponse resposta = DtoMapper.toResponse(salva);
        publicador.publicarEntrega(loja.getId(), "CRIADA", resposta);
        return Optional.of(resposta);
    }

    private static PedidoExterno pedidoExterno(String provedor, Estabelecimento loja, PedidoRecebido recebido) {
        PedidoExterno pedido = new PedidoExterno();
        pedido.setEstabelecimento(loja);
        pedido.setProvedor(provedor);
        pedido.setIdExterno(recebido.idExterno());
        pedido.setNumeroExibicao(recebido.numeroExibicao());
        pedido.setStatusExterno(recebido.statusExterno());
        pedido.setNomeCliente(recebido.nomeCliente());
        pedido.setTelefoneCliente(recebido.telefoneCliente());
        pedido.setValorItens(recebido.valorItens());
        pedido.setValorTaxaEntrega(recebido.valorTaxaEntrega());
        pedido.setValorTotal(recebido.valorTotal());
        pedido.setFormaPagamento(recebido.formaPagamento());
        pedido.setPagoOnline(recebido.pagoOnline());
        pedido.setTrocoPara(recebido.trocoPara());
        pedido.setObservacoes(recebido.observacoes());
        pedido.setPayloadJson(recebido.payloadJson());
        if (recebido.itens() != null) {
            recebido.itens().forEach(item -> {
                ItemPedido novo = new ItemPedido();
                novo.setDescricao(item.descricao());
                novo.setQuantidade(item.quantidade());
                novo.setValorUnitario(item.valorUnitario());
                novo.setObservacao(item.observacao());
                pedido.adicionarItem(novo);
            });
        }
        return pedido;
    }

    /** A entrega copia cliente e pagamento do pedido (DVP 1.5.3). */
    private static Entrega entrega(Estabelecimento loja, PedidoExterno pedido, PedidoRecebido recebido) {
        Entrega entrega = new Entrega();
        entrega.setEstabelecimento(loja);
        entrega.setPedidoExterno(pedido);
        entrega.setNomeCliente(recebido.nomeCliente());
        entrega.setTelefoneCliente(recebido.telefoneCliente());
        entrega.setEnderecoCompleto(recebido.enderecoCompleto());
        entrega.setBairro(recebido.bairro());
        entrega.setCep(recebido.cep());
        entrega.setComplemento(recebido.complemento());
        entrega.setPontoReferencia(recebido.pontoReferencia());
        entrega.setCoordenada(copia(recebido.coordenada()));
        entrega.setValorPedido(recebido.valorTotal());
        entrega.setFormaPagamento(recebido.formaPagamento());
        entrega.setPagoOnline(recebido.pagoOnline());
        entrega.setTrocoPara(recebido.trocoPara());
        entrega.setObservacoes(recebido.observacoes());
        entrega.setStatus(StatusEntrega.AGUARDANDO_DESPACHO);
        entrega.setPendenteRevisao(false);
        return entrega;
    }

    private static Coordenada copia(Coordenada coordenada) {
        if (coordenada == null || !coordenada.preenchida()) {
            return null;
        }
        return Coordenadas.validada(coordenada.getLatitude(), coordenada.getLongitude());
    }
}
