package br.com.agilizadelivery.dto.request;

import br.com.agilizadelivery.service.CanalAcesso;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Credenciais do login (UC07).
 *
 * canal: BACKOFFICE (padrao, quando ausente) ou APP. PROVISORIO: o DVP nao
 * diz como o cliente informa o canal; sem o campo, vale o backoffice, de
 * modo que o entregador que esquecer o campo e recusado (UC07 E4), e nao
 * aceito por engano.
 */
public record LoginRequest(
        @NotBlank @Email String loginEmail,
        @NotBlank String senha,
        CanalAcesso canal) {

    public LoginRequest(String loginEmail, String senha) {
        this(loginEmail, senha, null);
    }

    /** A senha nunca aparece em log (Constituicao 6.2). */
    @Override
    public String toString() {
        return "LoginRequest[loginEmail=" + loginEmail + ", senha=***, canal=" + canal + "]";
    }

    public CanalAcesso canalOuPadrao() {
        return canal == null ? CanalAcesso.BACKOFFICE : canal;
    }
}
