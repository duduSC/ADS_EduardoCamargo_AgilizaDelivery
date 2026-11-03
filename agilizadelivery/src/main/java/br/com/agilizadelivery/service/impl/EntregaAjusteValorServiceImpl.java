package br.com.agilizadelivery.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.dto.DtoMapper;
import br.com.agilizadelivery.dto.response.EntregaAjusteValorResponse;
import br.com.agilizadelivery.repository.EntregaAjusteValorRepository;
import br.com.agilizadelivery.service.EntregaAjusteValorService;

@Service
@Transactional(readOnly = true)
public class EntregaAjusteValorServiceImpl implements EntregaAjusteValorService {

    private final EntregaAjusteValorRepository repository;

    public EntregaAjusteValorServiceImpl(EntregaAjusteValorRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<EntregaAjusteValorResponse> listarPorEntrega(UUID estabelecimentoId, UUID entregaId) {
        return repository
                .findByEntregaEstabelecimentoIdAndEntregaIdOrderByAjustadoEmAsc(estabelecimentoId, entregaId)
                .stream()
                .map(DtoMapper::toResponse)
                .toList();
    }
}
