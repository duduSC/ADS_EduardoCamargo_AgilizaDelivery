package br.com.agilizadelivery.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.agilizadelivery.dto.request.CriarUsuarioRequest;
import br.com.agilizadelivery.dto.response.UsuarioResponse;
import br.com.agilizadelivery.seguranca.Perfis;
import br.com.agilizadelivery.seguranca.jwt.UsuarioLogado;
import br.com.agilizadelivery.service.UsuarioService;
import jakarta.validation.Valid;

/**
 * Gestao de usuarios do estabelecimento (UC05).
 *
 * Perfil LOJA (D004): somente ADMIN_RESTAURANTE.
 * O estabelecimentoId vem do token (Constituicao 5.6).
 */
@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize(Perfis.LOJA)
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /** Lista usuarios do estabelecimento. */
    @GetMapping
    public List<UsuarioResponse> listar(@AuthenticationPrincipal UsuarioLogado logado) {
        return usuarioService.listarPorEstabelecimento(logado.estabelecimentoObrigatorio());
    }

    /** Criar usuario no estabelecimento do token. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse criar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @Valid @RequestBody CriarUsuarioRequest request) {
        return usuarioService.criar(logado.estabelecimentoObrigatorio(), request);
    }
}
