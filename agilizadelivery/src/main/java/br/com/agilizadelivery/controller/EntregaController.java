package br.com.agilizadelivery.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.agilizadelivery.dto.request.AjustarCoordenadaRequest;
import br.com.agilizadelivery.dto.request.CancelarEntregaRequest;
import br.com.agilizadelivery.dto.request.CriarEntregaAvulsaRequest;
import br.com.agilizadelivery.dto.request.ResolverRevisaoRequest;
import br.com.agilizadelivery.dto.response.EntregaResponse;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.seguranca.Perfis;
import br.com.agilizadelivery.seguranca.jwt.UsuarioLogado;
import br.com.agilizadelivery.service.EntregaService;
import jakarta.validation.Valid;

/**
 * Endpoints REST da tela de entregas do backoffice (F2: pendentes,
 * revisao, entrega avulsa).
 *
 * O estabelecimentoId vem sempre do token (Constituicao 5.6); nenhum
 * parametro de cliente substitui essa fonte. Acesso restrito a
 * OPERADOR e ADMIN_RESTAURANTE (D004).
 */
@RestController
@RequestMapping("/api/entregas")
@PreAuthorize(Perfis.OPERACAO)
public class EntregaController {

    private final EntregaService entregaService;

    public EntregaController(EntregaService entregaService) {
        this.entregaService = entregaService;
    }

    /**
     * Lista entregas do estabelecimento. Filtros opcionais:
     * - status: filtra por StatusEntrega (ex.: AGUARDANDO_DESPACHO)
     * - pendenteRevisao=true: fila de revisao (HU01/HU03)
     *
     * Sem filtro: devolve as pendentes de despacho (sem revisao em aberto),
     * que e o cenario padrao da tela de entregas.
     */
    @GetMapping
    public List<EntregaResponse> listar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @RequestParam(required = false) StatusEntrega status,
            @RequestParam(required = false) Boolean pendenteRevisao) {
        UUID estabelecimentoId = logado.estabelecimentoObrigatorio();
        if (Boolean.TRUE.equals(pendenteRevisao)) {
            return entregaService.listarPendentesDeRevisao(estabelecimentoId);
        }
        if (status != null) {
            return entregaService.listarPorEstabelecimentoEStatus(estabelecimentoId, status);
        }
        return entregaService.listarPendentesDeDespacho(estabelecimentoId);
    }

    @GetMapping("/{id}")
    public EntregaResponse buscarPorId(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id) {
        return entregaService.buscarPorId(logado.estabelecimentoObrigatorio(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntregaResponse criarAvulsa(
            @AuthenticationPrincipal UsuarioLogado logado,
            @Valid @RequestBody CriarEntregaAvulsaRequest request) {
        return entregaService.criarAvulsa(logado.estabelecimentoObrigatorio(), request);
    }

    /** A cozinha concluiu o pedido (UC01, passo 5). */
    @PostMapping("/{id}/pronto")
    public EntregaResponse marcarPronta(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id) {
        return entregaService.marcarProntaParaDespacho(
                logado.estabelecimentoObrigatorio(), id, null);
    }

    /** Reposicionamento manual do pino (UC01 A1, risco R04). */
    @PatchMapping("/{id}/coordenada")
    public EntregaResponse ajustarCoordenada(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id,
            @Valid @RequestBody AjustarCoordenadaRequest request) {
        return entregaService.ajustarCoordenada(
                logado.estabelecimentoObrigatorio(), id,
                request.latitude(), request.longitude(),
                logado.usuarioId());
    }

    /** Cancelamento pelo operador (UC02 A3). */
    @PostMapping("/{id}/cancelar")
    public EntregaResponse cancelar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id,
            @Valid @RequestBody CancelarEntregaRequest request) {
        return entregaService.cancelar(
                logado.estabelecimentoObrigatorio(), id,
                logado.usuarioId(), request.motivo());
    }

    /** Resolucao de contestacao pelo operador (HU03). */
    @PostMapping("/{id}/revisao/resolver")
    public EntregaResponse resolverRevisao(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id,
            @Valid @RequestBody ResolverRevisaoRequest request) {
        return entregaService.resolverRevisao(
                logado.estabelecimentoObrigatorio(), id,
                logado.usuarioId(),
                request.valorRepasse(), request.motivo());
    }
}
