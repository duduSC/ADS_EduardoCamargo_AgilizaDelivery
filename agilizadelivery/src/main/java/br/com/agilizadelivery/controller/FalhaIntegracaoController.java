package br.com.agilizadelivery.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.agilizadelivery.dto.response.FalhaIntegracaoResponse;
import br.com.agilizadelivery.entity.model.enums.StatusFalhaIntegracao;
import br.com.agilizadelivery.seguranca.Perfis;
import br.com.agilizadelivery.seguranca.jwt.UsuarioLogado;
import br.com.agilizadelivery.service.FalhaIntegracaoService;

/**
 * Fila de falhas de integracao (RNF04).
 *
 * Listagem: OPERACAO (D004).
 * Reprocessar e descartar: LOJA (ADMIN_RESTAURANTE).
 * O estabelecimentoId vem do token (Constituicao 5.6).
 */
@RestController
@RequestMapping("/api/falhas-integracao")
public class FalhaIntegracaoController {

    private final FalhaIntegracaoService falhaIntegracaoService;

    public FalhaIntegracaoController(FalhaIntegracaoService falhaIntegracaoService) {
        this.falhaIntegracaoService = falhaIntegracaoService;
    }

    /** Lista falhas por status (ex.: PENDENTE). */
    @GetMapping
    @PreAuthorize(Perfis.OPERACAO)
    public List<FalhaIntegracaoResponse> listar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @RequestParam(defaultValue = "PENDENTE") StatusFalhaIntegracao status) {
        return falhaIntegracaoService.listarPorStatus(logado.estabelecimentoObrigatorio(), status);
    }

    /** Marcar falha como reprocessada. */
    @PostMapping("/{id}/reprocessar")
    @PreAuthorize(Perfis.LOJA)
    public FalhaIntegracaoResponse reprocessar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable Long id) {
        return falhaIntegracaoService.marcarReprocessada(logado.estabelecimentoObrigatorio(), id);
    }

    /** Descartar falha. */
    @PostMapping("/{id}/descartar")
    @PreAuthorize(Perfis.LOJA)
    public FalhaIntegracaoResponse descartar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable Long id) {
        return falhaIntegracaoService.descartar(logado.estabelecimentoObrigatorio(), id);
    }
}
