package br.com.agilizadelivery.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import jakarta.validation.ConstraintViolationException;

/**
 * Coordenada da loja obrigatoria no mapeamento (DVP 1.5.4: ESTABELECIMENTO
 * latitude e longitude NOT NULL), contra o banco H2 do perfil de testes.
 */
@SpringBootTest
@Transactional
class EstabelecimentoPersistenciaTest {

    @Autowired
    private EstabelecimentoRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    /** Estabelecimento valido e ainda nao persistido. */
    static Estabelecimento novoEstabelecimento() {
        Estabelecimento estabelecimento = new Estabelecimento();
        estabelecimento.setRazaoSocial("Forno Nobre Ltda");
        estabelecimento.setNomeFantasia("Pizzaria Forno Nobre");
        estabelecimento.setCnpj("11222333000181");
        estabelecimento.setEnderecoCompleto("Rua das Flores, 100 - Centro");
        estabelecimento.setCoordenada(new Coordenada(new BigDecimal("-23.5505200"), new BigDecimal("-46.6333080")));
        estabelecimento.setStatus(StatusEstabelecimento.ATIVO);
        estabelecimento.setMaxPedidosPorLote((short) 5);
        return estabelecimento;
    }

    @Test
    @DisplayName("Colunas latitude e longitude da loja são NOT NULL no schema")
    void colunasDeCoordenadaSaoObrigatorias() {
        for (String coluna : new String[] {"latitude", "longitude"}) {
            String nulavel = jdbc.queryForObject(
                    "select is_nullable from information_schema.columns "
                            + "where table_name = 'estabelecimento' and column_name = ?",
                    String.class, coluna);
            assertEquals("NO", nulavel, coluna);
        }
    }

    @Test
    @DisplayName("Deve gravar a loja com coordenada completa")
    void deveGravarComCoordenada() {
        assertNotNull(repository.saveAndFlush(novoEstabelecimento()).getId());
    }

    @Test
    @DisplayName("Deve recusar a loja sem latitude")
    void deveRecusarSemLatitude() {
        Estabelecimento estabelecimento = novoEstabelecimento();
        estabelecimento.getCoordenada().setLatitude(null);

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(estabelecimento));
    }

    @Test
    @DisplayName("Deve recusar a loja sem coordenada")
    void deveRecusarSemCoordenada() {
        Estabelecimento estabelecimento = novoEstabelecimento();
        estabelecimento.setCoordenada(null);

        assertThrows(ConstraintViolationException.class, () -> repository.saveAndFlush(estabelecimento));
    }
}
