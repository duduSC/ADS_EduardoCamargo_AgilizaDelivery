package br.com.agilizadelivery.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.dto.DtoMapper;
import br.com.agilizadelivery.dto.request.AjustarRepasseRequest;
import br.com.agilizadelivery.dto.response.EntregaAjusteValorResponse;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.repository.EntregaAjusteValorRepository;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.service.EntregaAjusteValorService;
import br.com.agilizadelivery.service.support.AjusteRepasse;
import br.com.agilizadelivery.service.support.BuscadorTenant;

@Service
@Transactional(readOnly = true)
public class EntregaAjusteValorServiceImpl implements EntregaAjusteValorService {

    private final EntregaAjusteValorRepository repository;
    private final EntregaRepository entregaRepository;
    private final BuscadorTenant buscador;
    private final AjusteRepasse ajusteRepasse;

    public EntregaAjusteValorServiceImpl(
            EntregaAjusteValorRepository repository,
            EntregaRepository entregaRepository,
            BuscadorTenant buscador,
            AjusteRepasse ajusteRepasse) {
        this.repository = repository;
        this.entregaRepository = entregaRepository;
        this.buscador = buscador;
        this.ajusteRepasse = ajusteRepasse;
    }

    @Override
    @Transactional
    public EntregaAjusteValorResponse ajustar(
            UUID estabelecimentoId,
            UUID entregaId,
            UUID usuarioId,
            AjustarRepasseRequest request) {
        Entrega entrega = entregaRepository.findByIdAndEstabelecimentoId(entregaId, estabelecimentoId)
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(Entrega.class, entregaId));
        Usuario autor = buscador.usuarioComPerfil(
                estabelecimentoId, usuarioId, PerfilUsuario.OPERADOR, PerfilUsuario.ADMIN_RESTAURANTE);
        return DtoMapper.toResponse(ajusteRepasse.ajustar(entrega, autor, request.valorNovo(), request.motivo()));
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
