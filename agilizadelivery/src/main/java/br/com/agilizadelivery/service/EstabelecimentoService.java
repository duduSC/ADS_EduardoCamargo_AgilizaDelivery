package br.com.agilizadelivery.service;

import org.springframework.stereotype.Service;

import br.com.agilizadelivery.repository.EstabelecimentoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EstabelecimentoService {

    private final EstabelecimentoRepository estabelecimentoRepository;
}
