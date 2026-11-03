package br.com.agilizadelivery.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.entity.model.Recibo;
import br.com.agilizadelivery.repository.ReciboRepository;
import br.com.agilizadelivery.service.ReciboService;

@Service
@Transactional(readOnly = true)
public class ReciboServiceImpl implements ReciboService {

    private final ReciboRepository repository;

    public ReciboServiceImpl(ReciboRepository repository) {
        this.repository = repository;
    }
}
