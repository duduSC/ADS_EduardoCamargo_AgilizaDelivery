package br.com.agilizadelivery.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.entity.model.LoteEntrega;
import br.com.agilizadelivery.repository.LoteEntregaRepository;
import br.com.agilizadelivery.service.LoteEntregaService;

@Service
@Transactional(readOnly = true)
public class LoteEntregaServiceImpl implements LoteEntregaService {

    private final LoteEntregaRepository repository;

    public LoteEntregaServiceImpl(LoteEntregaRepository repository) {
        this.repository = repository;
    }
}
