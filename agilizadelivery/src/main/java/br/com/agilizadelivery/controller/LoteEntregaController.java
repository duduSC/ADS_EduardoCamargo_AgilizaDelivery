package br.com.agilizadelivery.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.agilizadelivery.dto.request.DespacharLoteRequest;
import br.com.agilizadelivery.dto.response.LoteEntregaResponse;
import br.com.agilizadelivery.dto.response.RotaLoteResponse;
import br.com.agilizadelivery.entity.model.enums.StatusLote;
import br.com.agilizadelivery.seguranca.Perfis;
import br.com.agilizadelivery.seguranca.jwt.UsuarioLogado;
import br.com.agilizadelivery.service.LoteEntregaService;
import jakarta.validation.Valid;

/**
 * Despacho e consulta de lotes (UC01, UC01 A4).
 *
 * Acesso OPERADOR e ADMIN_RESTAURANTE (D004). O estabelecimentoId vem
 * sempre do token (Constituicao 5.6).
 */
@RestController
@RequestMapping("/api/lotes")
@PreAuthorize(Perfis.OPERACAO)
public class LoteEntregaController {

    private final LoteEntregaService loteEntregaService;

    public LoteEntregaController(LoteEntregaService loteEntregaService) {
        this.loteEntregaService = loteEntregaService;
    }

    /** Despacha um lote para um entregador (UC01, passo 7-8). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoteEntregaResponse despachar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @Valid @RequestBody DespacharLoteRequest request) {
        return loteEntregaService.despachar(
                logado.estabelecimentoObrigatorio(),
                logado.usuarioId(),
                request);
    }

    @GetMapping("/{id}")
    public LoteEntregaResponse buscarPorId(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id) {
        return loteEntregaService.buscarPorId(logado.estabelecimentoObrigatorio(), id);
    }

    /** Lista lotes por status (ex.: DESPACHADO, EM_ROTA). */
    @GetMapping
    public List<LoteEntregaResponse> listar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @RequestParam(required = false) StatusLote status) {
        UUID estabelecimentoId = logado.estabelecimentoObrigatorio();
        if (status != null) {
            return loteEntregaService.listarPorEstabelecimentoEStatus(estabelecimentoId, status);
        }
        return loteEntregaService.listarPorEstabelecimentoEStatus(estabelecimentoId, StatusLote.DESPACHADO);
    }

    /** Devolucao do lote antes da saida (UC01 A4, so DESPACHADO). */
    @PostMapping("/{id}/devolver")
    public LoteEntregaResponse devolver(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id) {
        return loteEntregaService.devolverDespacho(
                logado.estabelecimentoObrigatorio(), id, logado.usuarioId());
    }

    /** Entregas do lote na ordem de paradas (GET /api/lotes/{id}/rota). */
    @GetMapping("/{id}/rota")
    public RotaLoteResponse buscarRota(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id) {
        return loteEntregaService.buscarRota(logado.estabelecimentoObrigatorio(), id);
    }
}
