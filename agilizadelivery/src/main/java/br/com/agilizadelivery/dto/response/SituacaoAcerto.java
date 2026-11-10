package br.com.agilizadelivery.dto.response;

/** Situacao de uma entrega finalizada em relacao ao acerto (UC03, UC08). */
public enum SituacaoAcerto {
    /** Apta a entrar no proximo recibo; soma no total devido. */
    A_ACERTAR,
    /** Ja vinculada a um recibo. */
    CONSOLIDADA,
    /** Pendente de revisao; fica fora do total ate a analise do operador. */
    EM_REVISAO
}
