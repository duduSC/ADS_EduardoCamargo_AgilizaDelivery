package br.com.agilizadelivery.service;

import org.springframework.stereotype.Service;

import br.com.agilizadelivery.repository.ItemPedidoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ItemPedidoService {

    private final ItemPedidoRepository itemPedidoRepository;
}
