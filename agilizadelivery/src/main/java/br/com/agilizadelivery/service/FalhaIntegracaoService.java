package br.com.agilizadelivery.service;

import org.springframework.stereotype.Service;

import br.com.agilizadelivery.repository.FalhaIntegracaoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FalhaIntegracaoService {

    private final FalhaIntegracaoRepository falhaIntegracaoRepository;
}
