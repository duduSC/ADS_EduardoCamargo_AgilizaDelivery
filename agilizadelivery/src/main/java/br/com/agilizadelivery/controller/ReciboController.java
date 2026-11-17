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

import br.com.agilizadelivery.dto.request.CancelarReciboRequest;
import br.com.agilizadelivery.dto.request.GerarReciboRequest;
import br.com.agilizadelivery.dto.response.ReciboResponse;
import br.com.agilizadelivery.seguranca.Perfis;
import br.com.agilizadelivery.seguranca.jwt.UsuarioLogado;
import br.com.agilizadelivery.service.ReciboService;
import jakarta.validation.Valid;

/**
 * Ciclo de vida do recibo: gerar, listar, consultar, pagar, cancelar
 * (UC03, HU07).
 *
 * Perfil OPERACAO (D004): OPERADOR e ADMIN_RESTAURANTE.
 * O estabelecimentoId vem do token (Constituicao 5.6).
 */
@RestController
@RequestMapping("/api/recibos")
@PreAuthorize(Perfis.OPERACAO)
public class ReciboController {

    private final ReciboService reciboService;

    public ReciboController(ReciboService reciboService) {
        this.reciboService = reciboService;
    }

    /**
     * Gera recibo de acerto (UC03 passo 5). O backend seleciona as entregas
     * que cabem no periodo informado.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReciboResponse gerar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @Valid @RequestBody GerarReciboRequest request) {
        return reciboService.gerar(
                logado.estabelecimentoObrigatorio(),
                logado.usuarioId(),
                request);
    }

    /**
     * Lista recibos. Filtros opcionais: motoboyId, caixaId.
     * Sem filtro: devolve todos os recibos do estabelecimento por caixa.
     */
    @GetMapping
    public List<ReciboResponse> listar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @RequestParam(required = false) UUID motoboyId,
            @RequestParam(required = false) UUID caixaId) {
        UUID estabelecimentoId = logado.estabelecimentoObrigatorio();
        if (caixaId != null) {
            return reciboService.listarPorCaixa(estabelecimentoId, caixaId);
        }
        if (motoboyId != null) {
            return reciboService.listarPorMotoboy(estabelecimentoId, motoboyId);
        }
        // Sem filtro: lista pelo caixa do dia via dataReferencia atual
        return reciboService.listarPorDataReferencia(estabelecimentoId, java.time.LocalDate.now());
    }

    @GetMapping("/{id}")
    public ReciboResponse buscarPorId(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id) {
        return reciboService.buscarPorId(logado.estabelecimentoObrigatorio(), id);
    }

    /** PIX confirmado fora da plataforma (UC03 passo 6): GERADO -> PAGO. */
    @PostMapping("/{id}/pagar")
    public ReciboResponse pagar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id) {
        return reciboService.marcarComoPago(
                logado.estabelecimentoObrigatorio(), id, logado.usuarioId());
    }

    /** Cancelar recibo (UC03 A2). So com caixa aberto. */
    @PostMapping("/{id}/cancelar")
    public ReciboResponse cancelar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id,
            @Valid @RequestBody CancelarReciboRequest request) {
        return reciboService.cancelar(
                logado.estabelecimentoObrigatorio(), id,
                logado.usuarioId(), request.motivo());
    }
}
