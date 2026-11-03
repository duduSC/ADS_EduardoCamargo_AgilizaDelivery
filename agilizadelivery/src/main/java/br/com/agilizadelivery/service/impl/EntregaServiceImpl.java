package br.com.agilizadelivery.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.service.EntregaService;

@Service
@Transactional(readOnly = true)
public class EntregaServiceImpl implements EntregaService {

    private final EntregaRepository repository;

    public EntregaServiceImpl(EntregaRepository repository) {
        this.repository = repository;
    }
}
