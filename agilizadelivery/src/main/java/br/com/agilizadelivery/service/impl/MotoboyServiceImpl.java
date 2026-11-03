package br.com.agilizadelivery.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.repository.MotoboyRepository;
import br.com.agilizadelivery.service.MotoboyService;

@Service
@Transactional(readOnly = true)
public class MotoboyServiceImpl implements MotoboyService {

    private final MotoboyRepository repository;

    public MotoboyServiceImpl(MotoboyRepository repository) {
        this.repository = repository;
    }
}
