package br.com.agilizadelivery.dto.response;

/**
 * Resposta do login e do refresh (D002): token de acesso, refresh token
 * (PROVISORIO, fora do DVP 3.1) e o usuario autenticado.
 */
public record LoginResponse(String token, String refreshToken, UsuarioAutenticadoResponse usuario) {

    @Override
    public String toString() {
        return "LoginResponse[usuario=" + usuario + "]";
    }
}
