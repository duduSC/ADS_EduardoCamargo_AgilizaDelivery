package br.com.agilizadelivery.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.dto.DtoMapper;
import br.com.agilizadelivery.dto.response.EntregaStatusHistoricoResponse;
import br.com.agilizadelivery.repository.EntregaStatusHistoricoRepository;
import br.com.agilizadelivery.service.EntregaStatusHistoricoService;

@Service
@Transactional(readOnly = true)
public class EntregaStatusHistoricoServiceImpl implements EntregaStatusHistoricoService {

    private final EntregaStatusHistoricoRepository repository;

    public EntregaStatusHistoricoServiceImpl(EntregaStatusHistoricoRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<EntregaStatusHistoricoResponse> listarPorEntrega(UUID estabelecimentoId, UUID entregaId) {
        return repository
                .findByEntregaEstabelecimentoIdAndEntregaIdOrderByRegistradoEmAsc(estabelecimentoId, entregaId)
                .stream()
                .map(DtoMapper::toResponse)
                .toList();
    }
}
