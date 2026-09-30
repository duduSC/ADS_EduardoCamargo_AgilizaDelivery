package br.com.agilizadelivery.entity.model;


import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor 
@AllArgsConstructor 
@Entity 
public class Motoboy {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)

    @Getter 
    @Setter 
    private Long id;
    
    @Getter
    @Setter
    private Long estabelecimento_id;
    
    @Column(unique = true)
    @Getter 
    @Setter 
    private String cpf;

    @Getter 
    @Setter 
    private String nome;

    @Getter
    @Setter
    private String telefone;

    @Getter 
    @Setter
    private String statusCadastro;

    @Getter
    @Setter
    private String statusDisponibilidade;

    @Getter
    @Setter
    private Double ultimaLatitude;

    @Getter
    @Setter
    private Double ultimaLongitude;

    @Getter
    @Setter
    private LocalDateTime dataUltimaAtualizacao;

    @Getter 
    @Setter
    private LocalDateTime ultima_posicao_em;

    @PrePersist
    public void prePersist() {
        this.dataUltimaAtualizacao = LocalDateTime.now();
    }

    @PreUpdate
    protected void preUpdate() {
        this.dataUltimaAtualizacao = LocalDateTime.now();
    }
}