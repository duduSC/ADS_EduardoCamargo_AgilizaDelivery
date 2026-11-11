package br.com.agilizadelivery.service;

import java.util.UUID;

import br.com.agilizadelivery.dto.request.LoginRequest;
import br.com.agilizadelivery.dto.response.UsuarioAutenticadoResponse;

/**
 * Regras de negocio da autenticacao (UC07, HU13). A emissao do token JWT
 * com os dados devolvidos cabe a camada de seguranca.
 */
public interface AutenticacaoService {

    UsuarioAutenticadoResponse autenticar(LoginRequest request, CanalAcesso canal);

    /**
     * Renovacao da sessao (refresh token, PROVISORIO: fora do DVP 3.1).
     * Refaz, com os dados atuais do banco, todas as verificacoes do login
     * exceto a senha: usuario ativo, canal do perfil, loja ATIVA e cadastro
     * do entregador ATIVO. Usuario inexistente responde como credencial
     * invalida.
     */
    UsuarioAutenticadoResponse reautenticar(UUID usuarioId, CanalAcesso canal);
}
