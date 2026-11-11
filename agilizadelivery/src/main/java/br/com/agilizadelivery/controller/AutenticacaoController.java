package br.com.agilizadelivery.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.agilizadelivery.dto.request.LoginRequest;
import br.com.agilizadelivery.dto.request.RefreshRequest;
import br.com.agilizadelivery.dto.response.LoginResponse;
import br.com.agilizadelivery.dto.response.UsuarioAutenticadoResponse;
import br.com.agilizadelivery.seguranca.jwt.ServicoJwt;
import br.com.agilizadelivery.service.AutenticacaoService;
import br.com.agilizadelivery.service.CanalAcesso;
import jakarta.validation.Valid;

/**
 * Login e renovacao de sessao (UC07). Rotas publicas.
 *
 * Erros: 401 CREDENCIAIS_INVALIDAS (e-mail ou senha, sem dizer qual),
 * 403 ACESSO_NEGADO (perfil no canal errado, UC07 E4), 403
 * ESTABELECIMENTO_INATIVO, 403 USUARIO_BLOQUEADO e, no refresh, 401
 * NAO_AUTENTICADO para refresh token invalido ou expirado.
 */
@RestController
@RequestMapping("/api/auth")
public class AutenticacaoController {

    private final AutenticacaoService autenticacaoService;
    private final ServicoJwt servicoJwt;

    public AutenticacaoController(AutenticacaoService autenticacaoService, ServicoJwt servicoJwt) {
        this.autenticacaoService = autenticacaoService;
        this.servicoJwt = servicoJwt;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        CanalAcesso canal = request.canalOuPadrao();
        return emitir(autenticacaoService.autenticar(request, canal), canal);
    }

    /**
     * PROVISORIO (refresh token fora do DVP 3.1): devolve um par novo de
     * tokens. O refresh antigo nao e revogado (nao ha armazenamento de
     * sessao); ele so deixa de valer ao expirar.
     */
    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        ServicoJwt.RefreshValido refresh = servicoJwt.validarRefreshToken(request.refreshToken());
        return emitir(autenticacaoService.reautenticar(refresh.usuarioId(), refresh.canal()), refresh.canal());
    }

    private LoginResponse emitir(UsuarioAutenticadoResponse usuario, CanalAcesso canal) {
        return new LoginResponse(
                servicoJwt.gerarTokenAcesso(usuario, canal),
                servicoJwt.gerarRefreshToken(usuario.usuarioId(), canal),
                usuario);
    }
}
