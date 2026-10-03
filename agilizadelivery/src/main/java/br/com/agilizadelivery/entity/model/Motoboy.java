package br.com.agilizadelivery.entity.model;

import java.time.LocalDateTime;

import br.com.agilizadelivery.entity.model.enums.StatusCadastroMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dados operacionais do entregador (DVP 1.5.4).
 *
 * A credencial de acesso fica em Usuario, ligado aqui por @OneToOne.
 * O estabelecimento do entregador e alcancado por esse vinculo:
 * motoboy.getUsuario().getEstabelecimento().
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "motoboy")
public class Motoboy extends EntidadeBase {

    /** Vinculo que permite o login no aplicativo (HU09). */
    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @NotBlank
    @Size(max = 120)
    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    /**
     * CPF cifrado em repouso com AES-GCM (RNF02 e RNF07). Como o IV e
     * aleatorio, o valor muda a cada gravacao e nao pode ser comparado:
     * a unicidade e garantida pelo indice cego em cpfHash.
     */
    @NotBlank
    @Column(name = "cpf_criptografado", nullable = false, length = 255)
    private String cpfCriptografado;

    /** Indice cego: HMAC-SHA256 do CPF. Detecta duplicidade (UC05 E2). */
    @NotBlank
    @Size(max = 64)
    @Column(name = "cpf_hash", nullable = false, unique = true, length = 64)
    private String cpfHash;

    @NotBlank
    @Size(max = 15)
    @Column(name = "telefone", nullable = false, length = 15)
    private String telefone;

    @Size(max = 8)
    @Column(name = "placa_veiculo", length = 8)
    private String placaVeiculo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status_cadastro", nullable = false, length = 10)
    private StatusCadastroMotoboy statusCadastro = StatusCadastroMotoboy.ATIVO;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status_disponibilidade", nullable = false, length = 10)
    private StatusDisponibilidadeMotoboy statusDisponibilidade = StatusDisponibilidadeMotoboy.OFFLINE;

    /** Ultima posicao conhecida, para a carga inicial do mapa (HU08). */
    @Valid
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "latitude", column = @Column(name = "ultima_latitude", precision = 10, scale = 7)),
            @AttributeOverride(name = "longitude", column = @Column(name = "ultima_longitude", precision = 10, scale = 7))
    })
    private Coordenada ultimaPosicao;

    /** Base do alerta de entregador sem transmitir (UC04 A1). */
    @Column(name = "ultima_posicao_em")
    private LocalDateTime ultimaPosicaoEm;

    /** Aceite do termo no primeiro acesso ao aplicativo (RNF07). */
    @Column(name = "consentimento_lgpd_em")
    private LocalDateTime consentimentoLgpdEm;

    @Size(max = 10)
    @Column(name = "consentimento_lgpd_versao", length = 10)
    private String consentimentoLgpdVersao;

    /**
     * Um entregador so pode receber lote estando ATIVO e ONLINE (HU02),
     * e com o consentimento LGPD registrado (RNF07).
     */
    public boolean podeReceberLote() {
        return StatusCadastroMotoboy.ATIVO.equals(this.statusCadastro)
                && StatusDisponibilidadeMotoboy.ONLINE.equals(this.statusDisponibilidade)
                && this.consentimentoLgpdEm != null;
    }
}
