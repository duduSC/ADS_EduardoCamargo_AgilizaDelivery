package br.com.agilizadelivery.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.agilizadelivery.dto.request.SalvarConfiguracaoIntegracaoRequest;
import br.com.agilizadelivery.dto.response.ConfiguracaoIntegracaoResponse;
import br.com.agilizadelivery.seguranca.Perfis;
import br.com.agilizadelivery.seguranca.jwt.UsuarioLogado;
import br.com.agilizadelivery.service.ConfiguracaoIntegracaoService;
import jakarta.validation.Valid;

/**
 * Vinculo da loja com provedores externos (UC05, HU09).
 *
 * Perfil LOJA (D004): somente ADMIN_RESTAURANTE.
 * O estabelecimentoId vem do token (Constituicao 5.6).
 */
@RestController
@RequestMapping("/api/integracoes")
@PreAuthorize(Perfis.LOJA)
public class ConfiguracaoIntegracaoController {

    private final ConfiguracaoIntegracaoService configuracaoIntegracaoService;

    public ConfiguracaoIntegracaoController(ConfiguracaoIntegracaoService configuracaoIntegracaoService) {
        this.configuracaoIntegracaoService = configuracaoIntegracaoService;
    }

    /** Configuracoes de integracao do estabelecimento. */
    @GetMapping
    public List<ConfiguracaoIntegracaoResponse> listar(@AuthenticationPrincipal UsuarioLogado logado) {
        return configuracaoIntegracaoService.listarPorEstabelecimento(logado.estabelecimentoObrigatorio());
    }

    /** Salvar/atualizar configuracao de integracao. */
    @PutMapping
    public ConfiguracaoIntegracaoResponse salvar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @Valid @RequestBody SalvarConfiguracaoIntegracaoRequest request) {
        return configuracaoIntegracaoService.salvar(logado.estabelecimentoObrigatorio(), request);
    }
}
