package br.com.agilizadelivery.controller;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.agilizadelivery.dto.response.ExtratoAcertoResponse;
import br.com.agilizadelivery.seguranca.Perfis;
import br.com.agilizadelivery.seguranca.jwt.UsuarioLogado;
import br.com.agilizadelivery.service.ExtratoAcertoService;

/**
 * Extrato do acerto financeiro do entregador (UC03 passos 2 e 3, UC08).
 *
 * Perfil OPERACAO (D004): OPERADOR e ADMIN_RESTAURANTE.
 * O estabelecimentoId vem do token (Constituicao 5.6).
 */
@RestController
@RequestMapping("/api/acertos")
@PreAuthorize(Perfis.OPERACAO)
public class AcertoController {

    private final ExtratoAcertoService extratoAcertoService;

    public AcertoController(ExtratoAcertoService extratoAcertoService) {
        this.extratoAcertoService = extratoAcertoService;
    }

    /**
     * Extrato do periodo para um entregador. Retorna totais calculados
     * pelo backend (totalDevido, totalConsolidado, totalEmRevisao,
     * possuiValorIndefinido) — o front nunca faz soma por conta propria.
     */
    @GetMapping
    public ExtratoAcertoResponse extrato(
            @AuthenticationPrincipal UsuarioLogado logado,
            @RequestParam UUID motoboyId,
            @RequestParam LocalDateTime inicio,
            @RequestParam LocalDateTime fim) {
        return extratoAcertoService.gerarExtrato(
                logado.estabelecimentoObrigatorio(), motoboyId, inicio, fim);
    }
}
