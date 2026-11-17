package br.com.agilizadelivery.integracao.pedidos;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import br.com.agilizadelivery.entity.model.ConfiguracaoIntegracao;
import br.com.agilizadelivery.entity.model.FalhaIntegracao;
import br.com.agilizadelivery.entity.model.enums.EtapaFalhaIntegracao;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import br.com.agilizadelivery.entity.model.enums.StatusFalhaIntegracao;
import br.com.agilizadelivery.repository.ConfiguracaoIntegracaoRepository;
import br.com.agilizadelivery.repository.FalhaIntegracaoRepository;
import br.com.agilizadelivery.service.support.ImportacaoPedido;
import lombok.extern.slf4j.Slf4j;

/**
 * Um ciclo do worker de integracao (DVP 1.5.6).
 *
 * Para cada CONFIGURACAO_INTEGRACAO ativa, de loja ATIVA, cujo provedor
 * tenha adapter: consulta os pedidos novos, importa cada um (idempotente)
 * e confirma o recebimento. Cada passo e repetido com recuo exponencial
 * por ate max-tentativas (RNF04); esgotadas, grava FALHA_INTEGRACAO
 * PENDENTE e segue para o proximo pedido ou a proxima loja, sem
 * interromper o ciclo.
 *
 * Desvio de ordem: a confirmacao (passo 4) acontece depois da criacao da
 * entrega (passo 5), para nunca confirmar ao provedor um pedido que nao
 * foi gravado.
 */
@Slf4j
@Component
public class CicloIntegracao {

    private final Map<String, ProvedorDePedidos> provedores;
    private final ConfiguracaoIntegracaoRepository configuracaoRepository;
    private final FalhaIntegracaoRepository falhaRepository;
    private final ImportacaoPedido importacao;
    private final IntegracaoPedidosProperties.Worker parametros;

    public CicloIntegracao(
            List<ProvedorDePedidos> provedores,
            ConfiguracaoIntegracaoRepository configuracaoRepository,
            FalhaIntegracaoRepository falhaRepository,
            ImportacaoPedido importacao,
            IntegracaoPedidosProperties properties) {
        this.provedores = provedores.stream()
                .collect(Collectors.toMap(ProvedorDePedidos::provedor, Function.identity()));
        this.configuracaoRepository = configuracaoRepository;
        this.falhaRepository = falhaRepository;
        this.importacao = importacao;
        this.parametros = properties.worker();
    }

    /** Executa um ciclo completo. Nunca lanca excecao. */
    public void executar() {
        for (ConfiguracaoIntegracao configuracao : configuracaoRepository.findAtivasComEstabelecimento()) {
            ProvedorDePedidos provedor = provedores.get(configuracao.getProvedor());
            if (provedor == null
                    || configuracao.getEstabelecimento().getStatus() != StatusEstabelecimento.ATIVO) {
                continue;
            }
            try {
                processar(configuracao, provedor);
            } catch (RuntimeException e) {
                log.error("Erro inesperado no ciclo de integração da configuração {}: {}",
                        configuracao.getId(), e.getMessage());
            }
        }
    }

    private void processar(ConfiguracaoIntegracao configuracao, ProvedorDePedidos provedor) {
        List<PedidoRecebido> pedidos;
        try {
            pedidos = comRecuo(() -> provedor.buscarNovosPedidos(configuracao));
        } catch (TentativasEsgotadas e) {
            registrarFalha(configuracao, null, EtapaFalhaIntegracao.CONSULTA, e, null);
            return;
        }
        configuracaoRepository.registrarPolling(configuracao.getId(), LocalDateTime.now());

        for (PedidoRecebido pedido : pedidos) {
            boolean importado;
            try {
                importado = comRecuo(() -> importacao.importar(configuracao, pedido)).isPresent();
            } catch (TentativasEsgotadas e) {
                registrarFalha(configuracao, pedido.idExterno(), EtapaFalhaIntegracao.PERSISTENCIA, e,
                        pedido.payloadJson());
                continue;
            }
            if (!importado) {
                continue;
            }
            try {
                comRecuo(() -> {
                    provedor.confirmarRecebimento(configuracao, pedido.idExterno());
                    return Boolean.TRUE;
                });
            } catch (TentativasEsgotadas e) {
                registrarFalha(configuracao, pedido.idExterno(), EtapaFalhaIntegracao.CONFIRMACAO, e,
                        pedido.payloadJson());
            }
        }
    }

    /** Repeticao com recuo exponencial: recuo, 2 x recuo, 4 x recuo... (RNF04). */
    private <T> T comRecuo(Supplier<T> passo) {
        Duration recuo = parametros.recuoInicial();
        RuntimeException ultima = null;
        for (int tentativa = 1; tentativa <= parametros.maxTentativas(); tentativa++) {
            try {
                return passo.get();
            } catch (RuntimeException e) {
                ultima = e;
                if (tentativa < parametros.maxTentativas()) {
                    aguardar(recuo);
                    recuo = recuo.multipliedBy(2);
                }
            }
        }
        throw new TentativasEsgotadas(parametros.maxTentativas(), ultima);
    }

    private static void aguardar(Duration recuo) {
        if (recuo.isZero() || recuo.isNegative()) {
            return;
        }
        try {
            Thread.sleep(recuo);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void registrarFalha(ConfiguracaoIntegracao configuracao, String idExterno,
            EtapaFalhaIntegracao etapa, TentativasEsgotadas erro, String payloadJson) {
        log.warn("Integração {} da loja {}: etapa {} falhou após {} tentativas (pedido {}): {}",
                configuracao.getProvedor(), configuracao.getEstabelecimento().getId(), etapa, erro.tentativas,
                idExterno, erro.getMessage());
        FalhaIntegracao falha = new FalhaIntegracao();
        falha.setConfiguracaoIntegracao(configuracao);
        falha.setIdExterno(idExterno);
        falha.setEtapa(etapa);
        falha.setMensagemErro(erro.getMessage());
        falha.setTentativas((short) erro.tentativas);
        falha.setPayloadJson(payloadJson);
        falha.setStatus(StatusFalhaIntegracao.PENDENTE);
        falha.setOcorridoEm(LocalDateTime.now());
        falhaRepository.save(falha);
    }

    /** Todas as tentativas de um passo falharam. */
    private static final class TentativasEsgotadas extends RuntimeException {

        private final int tentativas;

        TentativasEsgotadas(int tentativas, RuntimeException causa) {
            super(mensagem(causa), causa);
            this.tentativas = tentativas;
        }

        private static String mensagem(RuntimeException causa) {
            if (causa == null) {
                return "Falha sem detalhe.";
            }
            String texto = causa.getMessage();
            return causa.getClass().getSimpleName() + (texto == null || texto.isBlank() ? "" : ": " + texto);
        }
    }
}
