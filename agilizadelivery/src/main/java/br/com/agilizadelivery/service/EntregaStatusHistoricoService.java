package br.com.agilizadelivery.service;

import org.springframework.stereotype.Service;

import br.com.agilizadelivery.repository.EntregaStatusHistoricoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EntregaStatusHistoricoService {

    private final EntregaStatusHistoricoRepository entregaStatusHistoricoRepository;
}
