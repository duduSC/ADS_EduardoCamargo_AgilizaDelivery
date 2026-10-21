package br.com.agilizadelivery.service;

import org.springframework.stereotype.Service;

import br.com.agilizadelivery.repository.ConfiguracaoIntegracaoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConfiguracaoIntegracaoService {

    private final ConfiguracaoIntegracaoRepository configuracaoIntegracaoRepository;
}
