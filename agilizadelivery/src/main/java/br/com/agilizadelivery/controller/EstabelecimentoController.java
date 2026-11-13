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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.agilizadelivery.dto.request.AlterarStatusEstabelecimentoRequest;
import br.com.agilizadelivery.dto.request.CriarEstabelecimentoRequest;
import br.com.agilizadelivery.dto.request.EditarEstabelecimentoRequest;
import br.com.agilizadelivery.dto.response.EstabelecimentoResponse;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import br.com.agilizadelivery.seguranca.Perfis;
import br.com.agilizadelivery.seguranca.jwt.UsuarioLogado;
import br.com.agilizadelivery.service.EstabelecimentoService;
import jakarta.validation.Valid;

/**
 * Dois grupos de endpoints sob o mesmo controller:
 *
 * 1. /api/estabelecimento (singular) — dados da loja do token.
 *    OPERACAO para leitura, LOJA para escrita (D004).
 *
 * 2. /api/estabelecimentos (plural) — CRUD de restaurantes.
 *    Exclusivo de ADMIN_GERAL (UC06).
 *
 * O estabelecimentoId vem do token para (1) e do path para (2).
 */
@RestController
public class EstabelecimentoController {

    private final EstabelecimentoService estabelecimentoService;

    public EstabelecimentoController(EstabelecimentoService estabelecimentoService) {
        this.estabelecimentoService = estabelecimentoService;
    }

    // ========= /api/estabelecimento (singular, do token) =========

    /** Dados do estabelecimento do token. Perfil OPERACAO (D004). */
    @GetMapping("/api/estabelecimento")
    @PreAuthorize(Perfis.OPERACAO)
    public EstabelecimentoResponse meuEstabelecimento(@AuthenticationPrincipal UsuarioLogado logado) {
        return estabelecimentoService.buscarPorId(logado.estabelecimentoObrigatorio());
    }

    /** Editar parametros da loja (UC05, HU09). Perfil LOJA. */
    @PatchMapping("/api/estabelecimento")
    @PreAuthorize(Perfis.LOJA)
    public EstabelecimentoResponse editar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @Valid @RequestBody EditarEstabelecimentoRequest request) {
        return estabelecimentoService.editarParametros(logado.estabelecimentoObrigatorio(), request);
    }

    // ========= /api/estabelecimentos (plural, ADMIN_GERAL) =========

    /** Listar todos os restaurantes. ADMIN_GERAL (UC06). */
    @GetMapping("/api/estabelecimentos")
    @PreAuthorize(Perfis.PLATAFORMA)
    public List<EstabelecimentoResponse> listarTodos(
            @RequestParam(required = false) StatusEstabelecimento status) {
        if (status != null) {
            return estabelecimentoService.listarPorStatus(status);
        }
        return estabelecimentoService.listarTodos();
    }

    /** Criar restaurante + administrador inicial (UC06, HU11). ADMIN_GERAL. */
    @PostMapping("/api/estabelecimentos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Perfis.PLATAFORMA)
    public EstabelecimentoResponse criar(@Valid @RequestBody CriarEstabelecimentoRequest request) {
        return estabelecimentoService.criar(request);
    }

    /** Buscar restaurante por ID. ADMIN_GERAL. */
    @GetMapping("/api/estabelecimentos/{id}")
    @PreAuthorize(Perfis.PLATAFORMA)
    public EstabelecimentoResponse buscarPorId(@PathVariable UUID id) {
        return estabelecimentoService.buscarPorId(id);
    }

    /** Ativar ou inativar restaurante (UC06, HU12). ADMIN_GERAL. */
    @PatchMapping("/api/estabelecimentos/{id}/status")
    @PreAuthorize(Perfis.PLATAFORMA)
    public EstabelecimentoResponse alterarStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AlterarStatusEstabelecimentoRequest request) {
        return estabelecimentoService.alterarStatus(id, request.status());
    }
}
