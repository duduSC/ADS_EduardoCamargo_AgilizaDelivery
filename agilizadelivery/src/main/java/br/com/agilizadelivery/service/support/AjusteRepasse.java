package br.com.agilizadelivery.service.support;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.EntregaAjusteValor;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.exception.AjusteValorNaoPermitidoException;
import br.com.agilizadelivery.repository.EntregaAjusteValorRepository;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.tempo.HorarioLocal;

/**
 * Ajuste manual do repasse de uma entrega (UC03 A1, DVP 1.5.5 passo 6).
 *
 * So e aceito em entrega finalizada (o UC03 parte delas; antes, o passo 4
 * descartaria o ajuste), fora de recibo e com o caixa do dia aberto. Todo ajuste
 * grava a trilha em ENTREGA_AJUSTE_VALOR, com o valor anterior, o novo,
 * o autor e o motivo, que e obrigatorio.
 */
@Component
public class AjusteRepasse {

    private final EntregaRepository entregaRepository;
    private final EntregaAjusteValorRepository ajusteRepository;
    private final PoliticaCorrecao politicaCorrecao;
    private final EncerramentoLote encerramentoLote;

    public AjusteRepasse(
            EntregaRepository entregaRepository,
            EntregaAjusteValorRepository ajusteRepository,
            PoliticaCorrecao politicaCorrecao,
            EncerramentoLote encerramentoLote) {
        this.entregaRepository = entregaRepository;
        this.ajusteRepository = ajusteRepository;
        this.politicaCorrecao = politicaCorrecao;
        this.encerramentoLote = encerramentoLote;
    }

    public EntregaAjusteValor ajustar(Entrega entrega, Usuario autor, BigDecimal valorNovo, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new AjusteValorNaoPermitidoException("o motivo do ajuste é obrigatório");
        }
        if (valorNovo == null || valorNovo.signum() < 0) {
            throw new AjusteValorNaoPermitidoException("o novo valor deve ser maior ou igual a zero");
        }
        // PROVISORIO (T014): antes do estado final o valor e provisorio, e o congelamento o descartaria.
        if (!entrega.emEstadoFinal()) {
            throw new AjusteValorNaoPermitidoException(
                    "a entrega ainda não foi finalizada; o valor dela é provisório até lá");
        }
        politicaCorrecao.exigirAjustePermitido(entrega);

        BigDecimal valorAnterior = entrega.getValorRepasse() == null ? BigDecimal.ZERO : entrega.getValorRepasse();
        if (valorAnterior.compareTo(valorNovo) == 0) {
            throw new AjusteValorNaoPermitidoException("o novo valor é igual ao valor atual");
        }

        EntregaAjusteValor ajuste = new EntregaAjusteValor();
        ajuste.setEntrega(entrega);
        ajuste.setUsuario(autor);
        ajuste.setValorAnterior(valorAnterior);
        ajuste.setValorNovo(valorNovo);
        ajuste.setMotivo(motivo.trim());
        ajuste.setAjustadoEm(HorarioLocal.agora());

        entrega.setValorRepasse(valorNovo);
        entregaRepository.save(entrega);
        encerramentoLote.atualizarTotal(entrega.getLote());
        return ajusteRepository.save(ajuste);
    }
}
