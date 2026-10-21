package br.com.agilizadelivery.service;

import org.springframework.stereotype.Service;

import br.com.agilizadelivery.repository.DistanciaPrecoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DistanciaPrecoService {

    private final DistanciaPrecoRepository distanciaPrecoRepository;
}
