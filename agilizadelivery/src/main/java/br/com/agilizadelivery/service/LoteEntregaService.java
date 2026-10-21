package br.com.agilizadelivery.service;

import org.springframework.stereotype.Service;

import br.com.agilizadelivery.repository.LoteEntregaRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoteEntregaService {

    private final LoteEntregaRepository loteEntregaRepository;
}
