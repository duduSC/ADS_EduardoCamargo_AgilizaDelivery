package br.com.agilizadelivery.entity.model;

import java.time.LocalDateTime;

import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Credencial de acesso de qualquer perfil (DVP 1.5.4 e UC07).
 *
 * Nao herda de EntidadeDoEstabelecimento porque o perfil ADMIN_GERAL e
 * global: para ele a FK de estabelecimento fica nula. Para os demais
 * perfis o vinculo e obrigatorio, regra verificada na camada de servico.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "usuario")
public class Usuario extends EntidadeBase {

    /** Nulo apenas para ADMIN_GERAL, que nao pertence a nenhuma loja. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estabelecimento_id")
    private Estabelecimento estabelecimento;

    @NotBlank
    @Size(max = 120)
    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @NotBlank
    @Email
    @Size(max = 150)
    @Column(name = "login_email", nullable = false, unique = true, length = 150)
    private String loginEmail;

    /** Hash BCrypt, nunca a senha em texto claro (RNF02). */
    @NotBlank
    @Column(name = "senha_hash", nullable = false, length = 60)
    private String senhaHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "perfil", nullable = false, length = 20)
    private PerfilUsuario perfil;

    @NotNull
    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "ultimo_acesso_em")
    private LocalDateTime ultimoAcessoEm;

    /** Verdadeiro quando o usuario e o dono da plataforma (UC06). */
    public boolean isAdminGeral() {
        return PerfilUsuario.ADMIN_GERAL.equals(this.perfil);
    }
}
