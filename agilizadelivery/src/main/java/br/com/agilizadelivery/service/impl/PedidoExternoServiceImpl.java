package br.com.agilizadelivery.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.dto.DtoMapper;
import br.com.agilizadelivery.dto.response.PedidoExternoResponse;
import br.com.agilizadelivery.entity.model.ItemPedido;
import br.com.agilizadelivery.entity.model.PedidoExterno;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.exception.PedidoJaImportadoException;
import br.com.agilizadelivery.exception.RegraDeNegocioException;
import br.com.agilizadelivery.repository.PedidoExternoRepository;
import br.com.agilizadelivery.service.PedidoExternoService;
import br.com.agilizadelivery.service.support.BuscadorTenant;
import br.com.agilizadelivery.service.support.Provedores;
import br.com.agilizadelivery.tempo.HorarioLocal;

/**
 * Persistencia dos pedidos recebidos do provedor (HU03, RNF13).
 *
 * O par (provedor, idExterno) e a chave de idempotencia: um pedido
 * nunca e importado duas vezes. O provedor e normalizado (Provedores)
 * antes da verificacao, para "ifood" e "IFOOD" serem a mesma chave.
 */
@Service
@Transactional(readOnly = true)
public class PedidoExternoServiceImpl implements PedidoExternoService {

    private final PedidoExternoRepository repository;
    private final BuscadorTenant buscador;

    public PedidoExternoServiceImpl(PedidoExternoRepository repository, BuscadorTenant buscador) {
        this.repository = repository;
        this.buscador = buscador;
    }

    @Override
    @Transactional
    public PedidoExternoResponse importar(UUID estabelecimentoId, PedidoExterno pedido) {
        pedido.setProvedor(Provedores.normalizar(pedido.getProvedor()));
        if (jaImportado(pedido.getProvedor(), pedido.getIdExterno())) {
            throw new PedidoJaImportadoException(pedido.getProvedor(), pedido.getIdExterno());
        }
        pedido.setEstabelecimento(buscador.estabelecimentoAtivo(estabelecimentoId));
        pedido.setProcessadoEm(null);
        List<ItemPedido> itens = List.copyOf(pedido.getItens());
        pedido.getItens().clear();
        itens.forEach(pedido::adicionarItem);
        return DtoMapper.toResponse(repository.save(pedido));
    }

    @Override
    public PedidoExternoResponse buscarPorId(UUID estabelecimentoId, UUID pedidoExternoId) {
        return DtoMapper.toResponse(buscar(estabelecimentoId, pedidoExternoId));
    }

    @Override
    public List<PedidoExternoResponse> listarNaoProcessados(UUID estabelecimentoId) {
        return repository.findByEstabelecimentoIdAndProcessadoEmIsNull(estabelecimentoId).stream()
                .map(DtoMapper::toResponse)
                .toList();
    }

    /** Marca o momento em que a entrega correspondente foi criada. */
    @Override
    @Transactional
    public PedidoExternoResponse marcarProcessado(UUID estabelecimentoId, UUID pedidoExternoId) {
        PedidoExterno pedido = buscar(estabelecimentoId, pedidoExternoId);
        if (pedido.getProcessadoEm() != null) {
            throw new RegraDeNegocioException("O pedido já foi processado e convertido em entrega.");
        }
        pedido.setProcessadoEm(HorarioLocal.agora());
        return DtoMapper.toResponse(repository.save(pedido));
    }

    @Override
    public boolean jaImportado(String provedor, String idExterno) {
        return repository.existsByProvedorAndIdExterno(Provedores.normalizar(provedor), idExterno);
    }

    private PedidoExterno buscar(UUID estabelecimentoId, UUID pedidoExternoId) {
        return repository.findByIdAndEstabelecimentoId(pedidoExternoId, estabelecimentoId)
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(PedidoExterno.class, pedidoExternoId));
    }
}
