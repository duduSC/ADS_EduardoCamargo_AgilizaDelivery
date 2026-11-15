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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.agilizadelivery.dto.request.AtualizarPosicaoRequest;
import br.com.agilizadelivery.dto.request.CriarMotoboyRequest;
import br.com.agilizadelivery.dto.request.EditarMotoboyRequest;
import br.com.agilizadelivery.dto.response.MotoboyResponse;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.enums.StatusCadastroMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;
import br.com.agilizadelivery.exception.EntidadeNaoEncontradaException;
import br.com.agilizadelivery.seguranca.Perfis;
import br.com.agilizadelivery.seguranca.jwt.UsuarioLogado;
import br.com.agilizadelivery.service.EntregadorService;
import br.com.agilizadelivery.service.MotoboyService;
import jakarta.validation.Valid;

/**
 * Gestao da frota de entregadores (UC05) e consultas para o painel de
 * despacho (F3).
 *
 * Listagem e busca: OPERADOR e ADMIN_RESTAURANTE (D004).
 * Criar, editar, bloquear, desbloquear: somente ADMIN_RESTAURANTE.
 *
 * AlterarDisponibilidade e EM_ROTA nao sao do backoffice (S004).
 */
@RestController
@RequestMapping("/api/motoboys")
public class MotoboyController {

    private final MotoboyService motoboyService;
    private final EntregadorService entregadorService;

    public MotoboyController(MotoboyService motoboyService, EntregadorService entregadorService) {
        this.motoboyService = motoboyService;
        this.entregadorService = entregadorService;
    }

    /**
     * Lista entregadores do estabelecimento. Filtros opcionais:
     * - statusCadastro (ATIVO, BLOQUEADO)
     * - statusDisponibilidade (OFFLINE, ONLINE, EM_ROTA)
     */
    @GetMapping
    @PreAuthorize(Perfis.OPERACAO)
    public List<MotoboyResponse> listar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @RequestParam(required = false) StatusCadastroMotoboy statusCadastro,
            @RequestParam(required = false) StatusDisponibilidadeMotoboy statusDisponibilidade) {
        UUID estabelecimentoId = logado.estabelecimentoObrigatorio();
        if (statusCadastro != null) {
            return motoboyService.listarPorStatusCadastro(estabelecimentoId, statusCadastro);
        }
        if (statusDisponibilidade != null) {
            return motoboyService.listarPorStatusDisponibilidade(estabelecimentoId, statusDisponibilidade);
        }
        return motoboyService.listarPorEstabelecimento(estabelecimentoId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Perfis.OPERACAO)
    public MotoboyResponse buscarPorId(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id) {
        return motoboyService.buscarPorId(logado.estabelecimentoObrigatorio(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Perfis.LOJA)
    public MotoboyResponse criar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @Valid @RequestBody CriarMotoboyRequest request) {
        return motoboyService.criar(logado.estabelecimentoObrigatorio(), request);
    }

    /** Edicao de dados do entregador (UC05, passo 2). */
    @PutMapping("/{id}")
    @PreAuthorize(Perfis.LOJA)
    public MotoboyResponse editar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id,
            @Valid @RequestBody EditarMotoboyRequest request) {
        return motoboyService.editar(logado.estabelecimentoObrigatorio(), id, request);
    }

    /** Bloqueia o entregador (UC05, passo 2). */
    @PostMapping("/{id}/bloquear")
    @PreAuthorize(Perfis.LOJA)
    public MotoboyResponse bloquear(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id) {
        return motoboyService.alterarStatusCadastro(
                logado.estabelecimentoObrigatorio(), id, StatusCadastroMotoboy.BLOQUEADO);
    }

    /** Desbloqueia o entregador (UC05, passo 2). */
    @PostMapping("/{id}/desbloquear")
    @PreAuthorize(Perfis.LOJA)
    public MotoboyResponse desbloquear(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id) {
        return motoboyService.alterarStatusCadastro(
                logado.estabelecimentoObrigatorio(), id, StatusCadastroMotoboy.ATIVO);
    }

    /**
     * Recebe posicao GPS do app do entregador.
     *
     * Obsoleto desde a T009: usar PATCH /api/entregador/posicao, que tira o
     * entregador do token. Aqui, {id} diferente do entregador do token
     * responde 404, como um id inexistente.
     */
    @Deprecated(since = "T009")
    @PatchMapping("/{id}/posicao")
    @PreAuthorize(Perfis.ENTREGADOR)
    public MotoboyResponse atualizarPosicao(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id,
            @Valid @RequestBody AtualizarPosicaoRequest request) {
        UUID estabelecimentoId = EntregadorController.estabelecimento(logado);
        UUID proprio = entregadorService.motoboyDoUsuario(estabelecimentoId, logado.usuarioId());
        if (!proprio.equals(id)) {
            throw EntidadeNaoEncontradaException.porId(Motoboy.class, id);
        }
        return motoboyService.atualizarPosicao(
                estabelecimentoId, id,
                request.latitude(), request.longitude());
    }
}
