package br.com.agilizadelivery.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.agilizadelivery.dto.request.AlterarDisponibilidadeMotoboyRequest;
import br.com.agilizadelivery.dto.request.AlterarStatusEntregaRequest;
import br.com.agilizadelivery.dto.request.AtualizarPosicaoRequest;
import br.com.agilizadelivery.dto.response.AcertoDoEntregadorResponse;
import br.com.agilizadelivery.dto.response.EntregaDoEntregadorResponse;
import br.com.agilizadelivery.dto.response.LoteDoEntregadorResponse;
import br.com.agilizadelivery.dto.response.MotoboyResponse;
import br.com.agilizadelivery.dto.response.ReciboDoEntregadorDetalheResponse;
import br.com.agilizadelivery.dto.response.ReciboDoEntregadorResponse;
import br.com.agilizadelivery.exception.AcessoNegadoException;
import br.com.agilizadelivery.seguranca.Perfis;
import br.com.agilizadelivery.seguranca.jwt.UsuarioLogado;
import br.com.agilizadelivery.service.CanalAcesso;
import br.com.agilizadelivery.service.EntregadorService;
import jakarta.validation.Valid;

/**
 * Endpoints do aplicativo do entregador (UC02, UC08, D008).
 *
 * So o perfil ENTREGADOR, com token emitido para o canal APP (UC07 E4).
 * O entregador e sempre o do token: nenhum id de entregador vem do caminho
 * ou do corpo. Lote ou entrega de outro entregador responde 404, igual a um
 * id inexistente, para nao revelar que o recurso existe.
 */
@RestController
@RequestMapping("/api/entregador")
@PreAuthorize(Perfis.ENTREGADOR)
public class EntregadorController {

    private final EntregadorService entregadorService;

    public EntregadorController(EntregadorService entregadorService) {
        this.entregadorService = entregadorService;
    }

    /** Cadastro e disponibilidade do proprio entregador. */
    @GetMapping("/me")
    public MotoboyResponse me(@AuthenticationPrincipal UsuarioLogado logado) {
        return entregadorService.buscarProprio(estabelecimento(logado), logado.usuarioId());
    }

    /** ONLINE ou OFFLINE (DVP 1.4.4). EM_ROTA e definido pelo lote. */
    @PatchMapping("/disponibilidade")
    public MotoboyResponse alterarDisponibilidade(
            @AuthenticationPrincipal UsuarioLogado logado,
            @Valid @RequestBody AlterarDisponibilidadeMotoboyRequest request) {
        return entregadorService.alterarDisponibilidade(estabelecimento(logado), logado.usuarioId(), request);
    }

    /** Ultima posicao GPS; aceita so em ONLINE ou EM_ROTA (RNF07). */
    @PatchMapping("/posicao")
    public MotoboyResponse atualizarPosicao(
            @AuthenticationPrincipal UsuarioLogado logado,
            @Valid @RequestBody AtualizarPosicaoRequest request) {
        return entregadorService.atualizarPosicao(
                estabelecimento(logado), logado.usuarioId(), request.latitude(), request.longitude());
    }

    /** Lote DESPACHADO ou EM_ROTA do entregador, com entregas e itens; 204 se nao houver. */
    @GetMapping("/lote-ativo")
    public ResponseEntity<LoteDoEntregadorResponse> loteAtivo(@AuthenticationPrincipal UsuarioLogado logado) {
        return entregadorService.buscarLoteAtivo(estabelecimento(logado), logado.usuarioId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Saida da loja (UC02, passo 4): lote e entregas vao para EM_ROTA. */
    @PostMapping("/lotes/{id}/iniciar")
    public LoteDoEntregadorResponse iniciarLote(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id) {
        return entregadorService.iniciarLote(estabelecimento(logado), logado.usuarioId(), id);
    }

    /** ENTREGUE ou FALHA (com motivo) a partir de EM_ROTA (UC02, passo 5 e A1). */
    @PostMapping("/entregas/{id}/status")
    public EntregaDoEntregadorResponse alterarStatusEntrega(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id,
            @Valid @RequestBody AlterarStatusEntregaRequest request) {
        return entregadorService.alterarStatusEntrega(estabelecimento(logado), logado.usuarioId(), id, request);
    }

    /** Contestacao do valor (UC08 A1); recusada em revisao (E1) ou com caixa fechado (E2). */
    @PostMapping("/entregas/{id}/contestar")
    public EntregaDoEntregadorResponse contestar(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id) {
        return entregadorService.contestar(estabelecimento(logado), logado.usuarioId(), id);
    }

    /** Acerto do proprio entregador e status do caixa do dia (UC08). Sem periodo: hoje. */
    @GetMapping("/acerto")
    public AcertoDoEntregadorResponse acerto(
            @AuthenticationPrincipal UsuarioLogado logado,
            @RequestParam(required = false) LocalDateTime inicio,
            @RequestParam(required = false) LocalDateTime fim) {
        return entregadorService.acerto(estabelecimento(logado), logado.usuarioId(), inicio, fim);
    }

    /** Recibos do proprio entregador, do mais recente para o mais antigo (D013). */
    @GetMapping("/recibos")
    public List<ReciboDoEntregadorResponse> recibos(@AuthenticationPrincipal UsuarioLogado logado) {
        return entregadorService.listarRecibos(estabelecimento(logado), logado.usuarioId());
    }

    /** Recibo com as entregas; o de outro entregador responde 404 (D013). */
    @GetMapping("/recibos/{id}")
    public ReciboDoEntregadorDetalheResponse recibo(
            @AuthenticationPrincipal UsuarioLogado logado,
            @PathVariable UUID id) {
        return entregadorService.buscarRecibo(estabelecimento(logado), logado.usuarioId(), id);
    }

    /** Estabelecimento do token, exigindo o canal do aplicativo (UC07 E4). */
    static UUID estabelecimento(UsuarioLogado logado) {
        if (logado.canal() != CanalAcesso.APP) {
            throw new AcessoNegadoException("Estas operações são exclusivas do aplicativo do entregador.");
        }
        return logado.estabelecimentoObrigatorio();
    }
}
