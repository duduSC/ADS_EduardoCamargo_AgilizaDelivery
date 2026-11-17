package br.com.agilizadelivery.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.agilizadelivery.dto.request.SalvarDistanciaPrecoRequest;
import br.com.agilizadelivery.dto.response.DistanciaPrecoResponse;
import br.com.agilizadelivery.seguranca.Perfis;
import br.com.agilizadelivery.seguranca.jwt.UsuarioLogado;
import br.com.agilizadelivery.service.DistanciaPrecoService;
import jakarta.validation.Valid;

/**
 * Tabela de faixas de preco por distancia (UC05, HU10).
 *
 * Perfil LOJA (D004): somente ADMIN_RESTAURANTE.
 * O estabelecimentoId vem do token (Constituicao 5.6).
 */
@RestController
@RequestMapping("/api/faixas-preco")
@PreAuthorize(Perfis.LOJA)
public class DistanciaPrecoController {

    private final DistanciaPrecoService distanciaPrecoService;

    public DistanciaPrecoController(DistanciaPrecoService distanciaPrecoService) {
        this.distanciaPrecoService = distanciaPrecoService;
    }

    /** Faixas ativas do estabelecimento. */
    @GetMapping
    public List<DistanciaPrecoResponse> listar(@AuthenticationPrincipal UsuarioLogado logado) {
        return distanciaPrecoService.listarFaixasAtivas(logado.estabelecimentoObrigatorio());
    }

    /**
     * Salva a tabela inteira numa transacao (UC05, passo 4).
     * Validacoes no servico: comeca em 0, contigua, sem sobreposicao,
     * ateKm > deKm, valorPago > 0.
     */
    @PutMapping
    public List<DistanciaPrecoResponse> salvar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @Valid @RequestBody List<SalvarDistanciaPrecoRequest> faixas) {
        return distanciaPrecoService.substituirTabela(logado.estabelecimentoObrigatorio(), faixas);
    }
}
