package br.com.agilizadelivery.service.support;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Component;

import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.exception.AjusteValorNaoPermitidoException;
import br.com.agilizadelivery.exception.CaixaFechadoException;
import br.com.agilizadelivery.repository.CaixaRepository;

/**
 * Decide se os valores de uma entrega ainda aceitam correcao (DVP 2.9).
 *
 * O limite e o fechamento do caixa do dia, nao o pagamento. Alem disso,
 * o ajuste manual so vale para entrega fora de recibo: vinculada, ela
 * precisa antes ter o recibo cancelado (UC03 A1/A2, 1.5.5 passo 6).
 *
 * O caixa do dia e o da data em que a entrega foi finalizada; antes
 * disso, o do dia corrente.
 */
@Component
public class PoliticaCorrecao {

    private final CaixaRepository caixaRepository;

    public PoliticaCorrecao(CaixaRepository caixaRepository) {
        this.caixaRepository = caixaRepository;
    }

    /** Caixa do dia existente e ABERTO ou REABERTO. */
    public boolean caixaDoDiaAberto(Entrega entrega) {
        return caixaDoDia(entrega).map(Caixa::estaAberto).orElse(false);
    }

    /** Contestacao do entregador (UC08 E2): exige o caixa do dia aberto. */
    public void exigirCaixaDoDiaAberto(Entrega entrega, String operacao) {
        if (!caixaDoDiaAberto(entrega)) {
            throw new CaixaFechadoException(operacao);
        }
    }

    /** Ajuste manual de repasse (UC03 A1): sem recibo e com caixa do dia aberto. */
    public void exigirAjustePermitido(Entrega entrega) {
        if (!entrega.permiteAjusteDeValor()) {
            throw new AjusteValorNaoPermitidoException(String.format(
                    "a entrega está vinculada ao recibo '%s'; cancele o recibo antes de ajustar o valor "
                            + "e gere um novo acerto em seguida",
                    entrega.getRecibo().getId()));
        }
        Optional<Caixa> caixa = caixaDoDia(entrega);
        if (caixa.isEmpty()) {
            throw new AjusteValorNaoPermitidoException("não há caixa aberto para o dia da entrega");
        }
        if (!caixa.get().estaAberto()) {
            throw new CaixaFechadoException("ajustar o valor de repasse");
        }
    }

    private Optional<Caixa> caixaDoDia(Entrega entrega) {
        LocalDate data = entrega.getFinalizadaEm() == null
                ? LocalDate.now()
                : entrega.getFinalizadaEm().toLocalDate();
        return caixaRepository.findByEstabelecimentoIdAndDataReferencia(entrega.getEstabelecimento().getId(), data);
    }
}
