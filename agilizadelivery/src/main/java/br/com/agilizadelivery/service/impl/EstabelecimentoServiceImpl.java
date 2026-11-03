package br.com.agilizadelivery.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.dto.DtoMapper;
import br.com.agilizadelivery.dto.request.CriarEstabelecimentoRequest;
import br.com.agilizadelivery.dto.response.EstabelecimentoResponse;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import br.com.agilizadelivery.exception.CnpjJaCadastradoException;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.repository.EstabelecimentoRepository;
import br.com.agilizadelivery.service.EstabelecimentoService;

@Service
@Transactional(readOnly = true)
public class EstabelecimentoServiceImpl implements EstabelecimentoService {

    private final EstabelecimentoRepository repository;

    public EstabelecimentoServiceImpl(EstabelecimentoRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public EstabelecimentoResponse criar(CriarEstabelecimentoRequest request) {
        if (repository.existsByCnpj(request.cnpj())) {
            throw new CnpjJaCadastradoException(request.cnpj());
        }

        Coordenada coordenada = new Coordenada(
                request.coordenada().latitude(),
                request.coordenada().longitude());
        Estabelecimento estabelecimento = new Estabelecimento();
        estabelecimento.setRazaoSocial(request.razaoSocial());
        estabelecimento.setNomeFantasia(request.nomeFantasia());
        estabelecimento.setCnpj(request.cnpj());
        estabelecimento.setTelefone(request.telefone());
        estabelecimento.setEnderecoCompleto(request.enderecoCompleto());
        estabelecimento.setCoordenada(coordenada);
        estabelecimento.setMaxPedidosPorLote(request.maxPedidosPorLote());
        estabelecimento.setValorRepasseForaDeArea(request.valorRepasseForaDeArea());
        estabelecimento.setStatus(StatusEstabelecimento.ATIVO);

        return DtoMapper.toResponse(repository.save(estabelecimento));
    }

    @Override
    public EstabelecimentoResponse buscarPorId(UUID estabelecimentoId) {
        return DtoMapper.toResponse(repository.findById(estabelecimentoId)
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(
                        Estabelecimento.class,
                        estabelecimentoId)));
    }

    @Override
    public List<EstabelecimentoResponse> listarPorStatus(StatusEstabelecimento status) {
        return repository.findByStatus(status).stream()
                .map(DtoMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public EstabelecimentoResponse alterarStatus(UUID estabelecimentoId, StatusEstabelecimento status) {
        Estabelecimento estabelecimento = repository.findById(estabelecimentoId)
                .orElseThrow(() -> EntidadeNaoEncontradaException.porId(
                        Estabelecimento.class,
                        estabelecimentoId));
        estabelecimento.setStatus(status);
        return DtoMapper.toResponse(repository.save(estabelecimento));
    }
}
