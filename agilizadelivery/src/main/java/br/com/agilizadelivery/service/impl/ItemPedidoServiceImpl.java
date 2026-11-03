package br.com.agilizadelivery.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.dto.DtoMapper;
import br.com.agilizadelivery.dto.response.ItemPedidoResponse;
import br.com.agilizadelivery.repository.ItemPedidoRepository;
import br.com.agilizadelivery.service.ItemPedidoService;

@Service
@Transactional(readOnly = true)
public class ItemPedidoServiceImpl implements ItemPedidoService {

    private final ItemPedidoRepository repository;

    public ItemPedidoServiceImpl(ItemPedidoRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ItemPedidoResponse> listarPorPedido(UUID estabelecimentoId, UUID pedidoExternoId) {
        return repository.findByPedidoExternoEstabelecimentoIdAndPedidoExternoId(estabelecimentoId, pedidoExternoId)
                .stream()
                .map(DtoMapper::toResponse)
                .toList();
    }
}
