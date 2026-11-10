package br.com.agilizadelivery.service;

import br.com.agilizadelivery.dto.request.LoginRequest;
import br.com.agilizadelivery.dto.response.UsuarioAutenticadoResponse;

/**
 * Regras de negocio da autenticacao (UC07, HU13). A emissao do token JWT
 * com os dados devolvidos cabe a camada de seguranca.
 */
public interface AutenticacaoService {

    UsuarioAutenticadoResponse autenticar(LoginRequest request, CanalAcesso canal);
}
