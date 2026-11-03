package br.com.agilizadelivery.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.dto.DtoMapper;
import br.com.agilizadelivery.dto.request.SalvarDistanciaPrecoRequest;
import br.com.agilizadelivery.dto.response.DistanciaPrecoResponse;
import br.com.agilizadelivery.entity.model.DistanciaPreco;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.exception.FaixaPrecoInvalidaException;
import br.com.agilizadelivery.repository.DistanciaPrecoRepository;
import br.com.agilizadelivery.repository.EstabelecimentoRepository;
import br.com.agilizadelivery.service.DistanciaPrecoService;

@Service
@Transactional(readOnly = true)
public class DistanciaPrecoServiceImpl implements DistanciaPrecoService {

    private final DistanciaPrecoRepository repository;
    private final EstabelecimentoRepository estabelecimentoRepository;

    public DistanciaPrecoServiceImpl(
            DistanciaPrecoRepository repository,
            EstabelecimentoRepository estabelecimentoRepository) {
        this.repository = repository;
        this.estabelecimentoRepository = estabelecimentoRepository;
    }

    @Override
    @Transactional
    public List<DistanciaPrecoResponse> substituirTabela(
            UUID estabelecimentoId,
            List<SalvarDistanciaPrecoRequest> faixas) {
        Estabelecimento estabelecimento = estabelecimentoRepository.findById(estabelecimentoId)
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(Estabelecimento.class, estabelecimentoId));

        List<SalvarDistanciaPrecoRequest> ordenadas = validarEOrdenar(faixas);
        List<DistanciaPreco> existentes = repository
                .findByEstabelecimentoIdAndAtivoTrueOrderByDeKmAsc(estabelecimentoId);
        existentes.forEach(faixa -> faixa.setAtivo(false));
        repository.saveAll(existentes);
        repository.flush();

        List<DistanciaPreco> novas = ordenadas.stream()
                .map(request -> {
                    DistanciaPreco faixa = new DistanciaPreco();
                    faixa.setEstabelecimento(estabelecimento);
                    faixa.setDeKm(request.deKm());
                    faixa.setAteKm(request.ateKm());
                    faixa.setValorPago(request.valorPago());
                    faixa.setVigenteDesde(request.vigenteDesde());
                    faixa.setAtivo(true);
                    return faixa;
                })
                .toList();

        return repository.saveAllAndFlush(novas).stream()
                .map(DtoMapper::toResponse)
                .toList();
    }

    @Override
    public List<DistanciaPrecoResponse> listarFaixasAtivas(UUID estabelecimentoId) {
        return repository.findByEstabelecimentoIdAndAtivoTrueOrderByDeKmAsc(estabelecimentoId).stream()
                .map(DtoMapper::toResponse)
                .toList();
    }

    @Override
    public List<DistanciaPrecoResponse> listarHistorico(UUID estabelecimentoId) {
        return repository.findByEstabelecimentoIdOrderByVigenteDesdeDescDeKmAsc(estabelecimentoId).stream()
                .map(DtoMapper::toResponse)
                .toList();
    }

    @Override
    public DistanciaPrecoResponse buscarFaixaParaDistancia(UUID estabelecimentoId, BigDecimal distanciaKm) {
        if (distanciaKm == null || distanciaKm.signum() < 0) {
            throw new FaixaPrecoInvalidaException("a distância deve ser maior ou igual a zero");
        }

        DistanciaPreco faixa = repository
                .findByEstabelecimentoIdAndAtivoTrueOrderByDeKmAsc(estabelecimentoId)
                .stream()
                .filter(candidata -> candidata.contem(distanciaKm))
                .findFirst()
                .orElseThrow(() -> new EntidadeNaoEncontradaException(
                        String.format(
                                "Nenhuma faixa de preço encontrada para o estabelecimento '%s' e distância %s km.",
                                estabelecimentoId,
                                distanciaKm)));
        return DtoMapper.toResponse(faixa);
    }

    private List<SalvarDistanciaPrecoRequest> validarEOrdenar(List<SalvarDistanciaPrecoRequest> faixas) {
        if (faixas == null || faixas.isEmpty()) {
            throw new FaixaPrecoInvalidaException("a tabela deve conter ao menos uma faixa");
        }

        List<SalvarDistanciaPrecoRequest> ordenadas = new ArrayList<>(faixas);
        if (ordenadas.stream().anyMatch(faixa -> faixa == null
                || faixa.deKm() == null
                || faixa.ateKm() == null
                || faixa.valorPago() == null
                || faixa.vigenteDesde() == null)) {
            throw new FaixaPrecoInvalidaException("todos os campos da faixa são obrigatórios");
        }

        ordenadas.sort(Comparator.comparing(SalvarDistanciaPrecoRequest::deKm));
        if (ordenadas.getFirst().deKm().compareTo(BigDecimal.ZERO) != 0) {
            throw new FaixaPrecoInvalidaException("a primeira faixa deve começar em 0 km");
        }

        for (int i = 0; i < ordenadas.size(); i++) {
            SalvarDistanciaPrecoRequest faixa = ordenadas.get(i);
            if (faixa.deKm().signum() < 0
                    || faixa.ateKm().compareTo(faixa.deKm()) <= 0
                    || faixa.valorPago().signum() <= 0) {
                throw new FaixaPrecoInvalidaException(
                        "os limites devem ser válidos e o valor pago deve ser maior que zero");
            }
            if (i > 0) {
                SalvarDistanciaPrecoRequest anterior = ordenadas.get(i - 1);
                if (faixa.deKm().compareTo(anterior.ateKm()) != 0) {
                    throw new FaixaPrecoInvalidaException(
                            "as faixas não podem ter sobreposições nem lacunas");
                }
            }
        }
        return ordenadas;
    }
}
