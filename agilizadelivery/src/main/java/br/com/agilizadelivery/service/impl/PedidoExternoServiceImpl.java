package br.com.agilizadelivery.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.entity.model.PedidoExterno;
import br.com.agilizadelivery.repository.PedidoExternoRepository;
import br.com.agilizadelivery.service.PedidoExternoService;

@Service
@Transactional(readOnly = true)
public class PedidoExternoServiceImpl implements PedidoExternoService {

    private final PedidoExternoRepository repository;

    public PedidoExternoServiceImpl(PedidoExternoRepository repository) {
        this.repository = repository;
    }
}
