package br.com.agilizadelivery.dto;

import java.util.List;

import br.com.agilizadelivery.dto.response.CaixaResponse;
import br.com.agilizadelivery.dto.response.ConfiguracaoIntegracaoResponse;
import br.com.agilizadelivery.dto.response.DistanciaPrecoResponse;
import br.com.agilizadelivery.dto.response.EntregaAjusteValorResponse;
import br.com.agilizadelivery.dto.response.EntregaResponse;
import br.com.agilizadelivery.dto.response.EntregaStatusHistoricoResponse;
import br.com.agilizadelivery.dto.response.EstabelecimentoResponse;
import br.com.agilizadelivery.dto.response.FalhaIntegracaoResponse;
import br.com.agilizadelivery.dto.response.ItemPedidoResponse;
import br.com.agilizadelivery.dto.response.LoteEntregaResponse;
import br.com.agilizadelivery.dto.response.MotoboyResponse;
import br.com.agilizadelivery.dto.response.PedidoExternoResponse;
import br.com.agilizadelivery.dto.response.ReciboResponse;
import br.com.agilizadelivery.dto.response.UsuarioResponse;
import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.ConfiguracaoIntegracao;
import br.com.agilizadelivery.entity.model.DistanciaPreco;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.EntregaAjusteValor;
import br.com.agilizadelivery.entity.model.EntregaStatusHistorico;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.FalhaIntegracao;
import br.com.agilizadelivery.entity.model.ItemPedido;
import br.com.agilizadelivery.entity.model.LoteEntrega;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.PedidoExterno;
import br.com.agilizadelivery.entity.model.Recibo;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.embeddable.Coordenada;

public final class DtoMapper {

    private DtoMapper() {
    }

    public static CaixaResponse toResponse(Caixa caixa) {
        return new CaixaResponse(
                caixa.getId(),
                caixa.getEstabelecimento().getId(),
                caixa.getDataReferencia(),
                caixa.getAbertoEm(),
                caixa.getFechadoEm(),
                idDoUsuario(caixa.getUsuarioFechamento()),
                caixa.getStatus(),
                caixa.getQtdRecibos(),
                caixa.getValorTotal(),
                caixa.getReabertoEm(),
                idDoUsuario(caixa.getUsuarioReabertura()),
                caixa.getMotivoReabertura(),
                caixa.getCriadoEm(),
                caixa.getAtualizadoEm());
    }

    public static ConfiguracaoIntegracaoResponse toResponse(ConfiguracaoIntegracao configuracao) {
        return new ConfiguracaoIntegracaoResponse(
                configuracao.getId(),
                configuracao.getEstabelecimento().getId(),
                configuracao.getProvedor(),
                configuracao.getMerchantId(),
                configuracao.getAtivo(),
                configuracao.getUltimoPollingEm(),
                configuracao.getCriadoEm(),
                configuracao.getAtualizadoEm());
    }

    public static DistanciaPrecoResponse toResponse(DistanciaPreco distanciaPreco) {
        return new DistanciaPrecoResponse(
                distanciaPreco.getId(),
                distanciaPreco.getEstabelecimento().getId(),
                distanciaPreco.getDeKm(),
                distanciaPreco.getAteKm(),
                distanciaPreco.getValorPago(),
                distanciaPreco.getAtivo(),
                distanciaPreco.getVigenteDesde(),
                distanciaPreco.getCriadoEm(),
                distanciaPreco.getAtualizadoEm());
    }

    public static EntregaResponse toResponse(Entrega entrega) {
        return new EntregaResponse(
                entrega.getId(),
                entrega.getEstabelecimento().getId(),
                idDoPedido(entrega.getPedidoExterno()),
                idDoLote(entrega.getLote()),
                idDaFaixa(entrega.getFaixaPreco()),
                idDoRecibo(entrega.getRecibo()),
                entrega.getNomeCliente(),
                entrega.getTelefoneCliente(),
                entrega.getEnderecoCompleto(),
                entrega.getBairro(),
                entrega.getCep(),
                entrega.getComplemento(),
                entrega.getPontoReferencia(),
                toDto(entrega.getCoordenada()),
                entrega.getValorPedido(),
                entrega.getFormaPagamento(),
                entrega.getPagoOnline(),
                entrega.getTrocoPara(),
                entrega.getObservacoes(),
                entrega.getDistanciaKm(),
                entrega.getValorRepasse(),
                entrega.getStatus(),
                entrega.getMotivoFalha(),
                entrega.getPendenteRevisao(),
                entrega.getMotivoRevisao(),
                entrega.getProntoParaDespachoEm(),
                entrega.getDespachadaEm(),
                entrega.getFinalizadaEm(),
                entrega.getCriadoEm(),
                entrega.getAtualizadoEm());
    }

    public static EntregaAjusteValorResponse toResponse(EntregaAjusteValor ajuste) {
        return new EntregaAjusteValorResponse(
                ajuste.getId(),
                ajuste.getEntrega().getId(),
                ajuste.getUsuario().getId(),
                ajuste.getValorAnterior(),
                ajuste.getValorNovo(),
                ajuste.getMotivo(),
                ajuste.getAjustadoEm());
    }

    public static EntregaStatusHistoricoResponse toResponse(EntregaStatusHistorico historico) {
        return new EntregaStatusHistoricoResponse(
                historico.getId(),
                historico.getEntrega().getId(),
                historico.getStatusAnterior(),
                historico.getStatusNovo(),
                idDoUsuario(historico.getRegistradoPorUsuario()),
                toDto(historico.getCoordenada()),
                historico.getRegistradoEm());
    }

    public static EstabelecimentoResponse toResponse(Estabelecimento estabelecimento) {
        return new EstabelecimentoResponse(
                estabelecimento.getId(),
                estabelecimento.getRazaoSocial(),
                estabelecimento.getNomeFantasia(),
                estabelecimento.getCnpj(),
                estabelecimento.getTelefone(),
                estabelecimento.getEnderecoCompleto(),
                toDto(estabelecimento.getCoordenada()),
                estabelecimento.getStatus(),
                estabelecimento.getMaxPedidosPorLote(),
                estabelecimento.getValorRepasseForaDeArea(),
                estabelecimento.getCriadoEm(),
                estabelecimento.getAtualizadoEm());
    }

    public static FalhaIntegracaoResponse toResponse(FalhaIntegracao falha) {
        return new FalhaIntegracaoResponse(
                falha.getId(),
                falha.getConfiguracaoIntegracao().getId(),
                falha.getIdExterno(),
                falha.getEtapa(),
                falha.getTentativas(),
                falha.getStatus(),
                falha.getOcorridoEm(),
                falha.getResolvidoEm());
    }

    public static ItemPedidoResponse toResponse(ItemPedido item) {
        return new ItemPedidoResponse(
                item.getId(),
                item.getPedidoExterno().getId(),
                item.getDescricao(),
                item.getQuantidade(),
                item.getValorUnitario(),
                item.getObservacao());
    }

    public static LoteEntregaResponse toResponse(LoteEntrega lote) {
        return new LoteEntregaResponse(
                lote.getId(),
                lote.getEstabelecimento().getId(),
                lote.getMotoboy().getId(),
                lote.getUsuarioDespachante().getId(),
                lote.getStatus(),
                lote.getQtdEntregas(),
                lote.getValorTotalRepasse(),
                lote.getDespachadoEm(),
                lote.getConcluidoEm(),
                lote.getCriadoEm(),
                lote.getAtualizadoEm());
    }

    public static MotoboyResponse toResponse(Motoboy motoboy) {
        return new MotoboyResponse(
                motoboy.getId(),
                motoboy.getUsuario().getId(),
                motoboy.getUsuario().getNome(),
                motoboy.getTelefone(),
                motoboy.getPlacaVeiculo(),
                motoboy.getStatusCadastro(),
                motoboy.getStatusDisponibilidade(),
                toDto(motoboy.getUltimaPosicao()),
                motoboy.getUltimaPosicaoEm(),
                motoboy.getCriadoEm(),
                motoboy.getAtualizadoEm());
    }

    public static PedidoExternoResponse toResponse(PedidoExterno pedido) {
        List<ItemPedidoResponse> itens = pedido.getItens().stream()
                .map(DtoMapper::toResponse)
                .toList();

        return new PedidoExternoResponse(
                pedido.getId(),
                pedido.getEstabelecimento().getId(),
                pedido.getProvedor(),
                pedido.getIdExterno(),
                pedido.getNumeroExibicao(),
                pedido.getStatusExterno(),
                pedido.getNomeCliente(),
                pedido.getTelefoneCliente(),
                pedido.getValorItens(),
                pedido.getValorTaxaEntrega(),
                pedido.getValorTotal(),
                pedido.getFormaPagamento(),
                pedido.getPagoOnline(),
                pedido.getTrocoPara(),
                pedido.getObservacoes(),
                pedido.getProcessadoEm(),
                itens,
                pedido.getCriadoEm(),
                pedido.getAtualizadoEm());
    }

    public static ReciboResponse toResponse(Recibo recibo) {
        return new ReciboResponse(
                recibo.getId(),
                recibo.getEstabelecimento().getId(),
                recibo.getCaixa().getId(),
                recibo.getMotoboy().getId(),
                recibo.getUsuarioGerador().getId(),
                recibo.getDataReferencia(),
                recibo.getPeriodoInicio(),
                recibo.getPeriodoFim(),
                recibo.getQtdEntregas(),
                recibo.getValorTotal(),
                recibo.getStatus(),
                recibo.getPagoEm(),
                recibo.getCriadoEm(),
                recibo.getAtualizadoEm());
    }

    public static UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getEstabelecimento() == null ? null : usuario.getEstabelecimento().getId(),
                usuario.getNome(),
                usuario.getLoginEmail(),
                usuario.getPerfil(),
                usuario.getAtivo(),
                usuario.getUltimoAcessoEm(),
                usuario.getCriadoEm(),
                usuario.getAtualizadoEm());
    }

    private static CoordenadaDto toDto(Coordenada coordenada) {
        if (coordenada == null) {
            return null;
        }
        return new CoordenadaDto(coordenada.getLatitude(), coordenada.getLongitude());
    }

    private static java.util.UUID idDoUsuario(Usuario usuario) {
        return usuario == null ? null : usuario.getId();
    }

    private static java.util.UUID idDoPedido(PedidoExterno pedido) {
        return pedido == null ? null : pedido.getId();
    }

    private static java.util.UUID idDoLote(LoteEntrega lote) {
        return lote == null ? null : lote.getId();
    }

    private static java.util.UUID idDaFaixa(DistanciaPreco faixaPreco) {
        return faixaPreco == null ? null : faixaPreco.getId();
    }

    private static java.util.UUID idDoRecibo(Recibo recibo) {
        return recibo == null ? null : recibo.getId();
    }
}
