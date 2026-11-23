package br.com.agilizadelivery.controller;

import java.time.LocalDate;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.agilizadelivery.dto.response.RelatorioDoDiaResponse;
import br.com.agilizadelivery.seguranca.Perfis;
import br.com.agilizadelivery.seguranca.jwt.UsuarioLogado;
import br.com.agilizadelivery.service.RelatorioService;
import br.com.agilizadelivery.tempo.HorarioLocal;

/**
 * Relatorio simples do dia (D013, item 5): decisao de escopo do Eduardo,
 * fora dos relatorios analiticos que o DVP 1.2.4 deixa fora do MVP.
 *
 * Perfil OPERACAO (D004), canal BACKOFFICE. A loja vem do token
 * (Constituicao 5.6), e todos os totais sao calculados aqui.
 */
@RestController
@RequestMapping("/api/relatorios")
@PreAuthorize(Perfis.OPERACAO)
public class RelatorioController {

    private final RelatorioService relatorioService;

    public RelatorioController(RelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    /** Sem data, vale o dia de hoje em Sao Paulo. */
    @GetMapping("/dia")
    public RelatorioDoDiaResponse dia(
            @AuthenticationPrincipal UsuarioLogado logado,
            @RequestParam(required = false) LocalDate data) {
        return relatorioService.doDia(
                logado.estabelecimentoObrigatorio(), data == null ? HorarioLocal.hoje() : data);
    }
}
