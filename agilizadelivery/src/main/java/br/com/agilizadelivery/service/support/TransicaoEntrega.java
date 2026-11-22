package br.com.agilizadelivery.service.support;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.EntregaStatusHistorico;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.exception.TransicaoEstadoInvalidaException;
import br.com.agilizadelivery.repository.EntregaStatusHistoricoRepository;
import br.com.agilizadelivery.tempo.HorarioLocal;

/**
 * Unico ponto que altera o status de uma entrega.
 *
 * Valida a mudanca contra a maquina de estados (DVP 1.4.4) e grava a
 * trilha de auditoria, com o horario do evento na origem (RNF09).
 */
@Component
public class TransicaoEntrega {

    private final EntregaStatusHistoricoRepository historicoRepository;

    public TransicaoEntrega(EntregaStatusHistoricoRepository historicoRepository) {
        this.historicoRepository = historicoRepository;
    }

    /** Registra o nascimento da entrega, sem status anterior. */
    public void registrarCriacao(Entrega entrega, Usuario autor) {
        registrar(entrega, null, autor, null, HorarioLocal.agora());
    }

    public void transitar(
            Entrega entrega,
            StatusEntrega destino,
            Usuario autor,
            Coordenada coordenada,
            LocalDateTime ocorridoEm) {
        StatusEntrega atual = entrega.getStatus();
        if (!atual.podeTransitarPara(destino)) {
            throw new TransicaoEstadoInvalidaException("Entrega", atual, destino);
        }
        entrega.setStatus(destino);
        registrar(entrega, atual, autor, coordenada, ocorridoEm == null ? HorarioLocal.agora() : ocorridoEm);
    }

    private void registrar(
            Entrega entrega,
            StatusEntrega anterior,
            Usuario autor,
            Coordenada coordenada,
            LocalDateTime ocorridoEm) {
        EntregaStatusHistorico historico = new EntregaStatusHistorico();
        historico.setEntrega(entrega);
        historico.setStatusAnterior(anterior);
        historico.setStatusNovo(entrega.getStatus());
        historico.setRegistradoPorUsuario(autor);
        historico.setCoordenada(coordenada);
        historico.setRegistradoEm(ocorridoEm);
        historicoRepository.save(historico);
    }
}
