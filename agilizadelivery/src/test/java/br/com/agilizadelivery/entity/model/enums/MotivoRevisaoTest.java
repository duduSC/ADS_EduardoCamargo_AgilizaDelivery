package br.com.agilizadelivery.entity.model.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MotivoRevisaoTest {

    @Test
    @DisplayName("pendente_revisao tem so os tres motivos (Constituicao 5.2)")
    void soTresMotivos() {
        assertThat(MotivoRevisao.values()).extracting(Enum::name)
                .containsExactly("SEM_COORDENADA", "SEM_DISTANCIA", "CONTESTACAO_ENTREGADOR");
    }
}
