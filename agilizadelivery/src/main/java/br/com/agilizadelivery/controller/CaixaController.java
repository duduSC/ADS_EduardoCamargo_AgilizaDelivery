package br.com.agilizadelivery.controller;

import java.time.LocalDate;
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

import br.com.agilizadelivery.dto.request.AbrirCaixaRequest;
import br.com.agilizadelivery.dto.request.ReabrirCaixaRequest;
import br.com.agilizadelivery.dto.response.CaixaResponse;
import br.com.agilizadelivery.seguranca.Perfis;
import br.com.agilizadelivery.seguranca.jwt.UsuarioLogado;
import br.com.agilizadelivery.service.CaixaService;
import br.com.agilizadelivery.tempo.HorarioLocal;
import jakarta.validation.Valid;

/**
 * Ciclo de vida do caixa: abrir, consultar, fechar, reabrir
 * (UC03, maquina de estados da secao 1.4.4 do DVP).
 *
 * Operacoes gerais: perfil OPERACAO (D004).
 * Reabertura: exclusiva de ADMIN_RESTAURANTE (UC03 A3).
 * O estabelecimentoId vem do token (Constituicao 5.6).
 */
@RestController
@RequestMapping("/api/caixas")
public class CaixaController {

    private final CaixaService caixaService;

    public CaixaController(CaixaService caixaService) {
        this.caixaService = caixaService;
    }

    /** Caixa do dia corrente. */
    @GetMapping("/atual")
    @PreAuthorize(Perfis.OPERACAO)
    public CaixaResponse atual(@AuthenticationPrincipal UsuarioLogado logado) {
        return caixaService.buscarPorEstabelecimentoEData(
                logado.estabelecimentoObrigatorio(), HorarioLocal.hoje());
    }

    /** Historico de caixas por periodo. */
    @GetMapping
    @PreAuthorize(Perfis.OPERACAO)
    public List<CaixaResponse> listar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @RequestParam(required = false) LocalDate dataInicio,
            @RequestParam(required = false) LocalDate dataFim) {
        UUID estabelecimentoId = logado.estabelecimentoObrigatorio();
        if (dataInicio != null && dataFim != null) {
            return caixaService.listarPorPeriodo(estabelecimentoId, dataInicio, dataFim);
        }
        // Sem filtro: lista caixas dos ultimos 30 dias
        LocalDate hoje = HorarioLocal.hoje();
        return caixaService.listarPorPeriodo(estabelecimentoId, hoje.minusDays(30), hoje);
    }

    /** Abrir caixa para um dia (UC03, passo 1). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Perfis.OPERACAO)
    public CaixaResponse abrir(
            @AuthenticationPrincipal UsuarioLogado logado,
            @Valid @RequestBody AbrirCaixaRequest request) {
        return caixaService.abrirCaixa(logado.estabelecimentoObrigatorio(), request);
    }

    /** Fechar caixa, tornando o periodo imutavel (UC03, passo 7). */
    @PostMapping("/{id}/fechar")
    @PreAuthorize(Perfis.OPERACAO)
    public CaixaResponse fechar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id) {
        return caixaService.fecharCaixa(
                logado.estabelecimentoObrigatorio(), id, logado.usuarioId());
    }

    /**
     * Reabrir caixa fechado (UC03 A3). Exclusivo de ADMIN_RESTAURANTE (D004).
     * Exige motivo obrigatorio.
     */
    @PostMapping("/{id}/reabrir")
    @PreAuthorize(Perfis.LOJA)
    public CaixaResponse reabrir(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id,
            @Valid @RequestBody ReabrirCaixaRequest request) {
        return caixaService.reabrirCaixa(
                logado.estabelecimentoObrigatorio(), id,
                logado.usuarioId(), request.motivo());
    }
}
