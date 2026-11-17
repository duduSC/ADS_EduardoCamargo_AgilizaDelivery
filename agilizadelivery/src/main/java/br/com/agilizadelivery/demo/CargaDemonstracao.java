package br.com.agilizadelivery.demo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import br.com.agilizadelivery.dto.CoordenadaDto;
import br.com.agilizadelivery.dto.request.AbrirCaixaRequest;
import br.com.agilizadelivery.dto.request.AdministradorInicialRequest;
import br.com.agilizadelivery.dto.request.CriarEntregaAvulsaRequest;
import br.com.agilizadelivery.dto.request.CriarEstabelecimentoRequest;
import br.com.agilizadelivery.dto.request.CriarMotoboyRequest;
import br.com.agilizadelivery.dto.request.CriarUsuarioRequest;
import br.com.agilizadelivery.dto.request.SalvarConfiguracaoIntegracaoRequest;
import br.com.agilizadelivery.dto.request.SalvarDistanciaPrecoRequest;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.enums.FormaPagamento;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.repository.CaixaRepository;
import br.com.agilizadelivery.repository.ConfiguracaoIntegracaoRepository;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.EstabelecimentoRepository;
import br.com.agilizadelivery.repository.UsuarioRepository;
import br.com.agilizadelivery.service.CaixaService;
import br.com.agilizadelivery.service.ConfiguracaoIntegracaoService;
import br.com.agilizadelivery.service.DistanciaPrecoService;
import br.com.agilizadelivery.service.EntregaService;
import br.com.agilizadelivery.service.EstabelecimentoService;
import br.com.agilizadelivery.service.MotoboyService;
import br.com.agilizadelivery.service.UsuarioService;
import br.com.agilizadelivery.service.support.CadastroUsuario;
import br.com.agilizadelivery.service.support.Provedores;
import lombok.extern.slf4j.Slf4j;

/**
 * Carga de demonstracao, SO PARA DESENVOLVIMENTO (D009, T010).
 *
 * Ligada por agiliza.demo.carga-inicial (true no perfil h2; false no
 * padrao e no postgres; AGILIZA_DEMO=true liga em qualquer perfil).
 *
 * Tudo passa pelos services e por CadastroUsuario, para a senha ser
 * gravada so como hash BCrypt e o CPF cifrado (RNF02). Idempotente:
 * cada parte so e criada se ainda nao existir, entao reiniciar a
 * aplicacao sobre um PostgreSQL ja carregado nao duplica nada.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "agiliza.demo", name = "carga-inicial", havingValue = "true")
public class CargaDemonstracao implements ApplicationRunner {

    public static final String EMAIL_ADMIN_GERAL = "admin@agiliza.dev";
    public static final String EMAIL_ADMIN_LOJA = "loja@agiliza.dev";
    public static final String EMAIL_OPERADOR = "operador@agiliza.dev";
    public static final String EMAIL_ENTREGADOR_1 = "entregador1@agiliza.dev";
    public static final String EMAIL_ENTREGADOR_2 = "entregador2@agiliza.dev";

    /** CNPJ ficticio, com digitos verificadores validos. */
    public static final String CNPJ_LOJA = "11222333000181";

    /** merchantId da integracao SIMULADOR da loja demo (T011). */
    public static final String MERCHANT_SIMULADOR = "demo-simulador";

    /** Loja no centro de Passo Fundo/RS. */
    static final BigDecimal LATITUDE_LOJA = new BigDecimal("-28.2620000");
    static final BigDecimal LONGITUDE_LOJA = new BigDecimal("-52.4083000");

    private final String senha;
    private final CadastroUsuario cadastroUsuario;
    private final UsuarioRepository usuarioRepository;
    private final EstabelecimentoRepository estabelecimentoRepository;
    private final CaixaRepository caixaRepository;
    private final EntregaRepository entregaRepository;
    private final EstabelecimentoService estabelecimentoService;
    private final UsuarioService usuarioService;
    private final MotoboyService motoboyService;
    private final DistanciaPrecoService distanciaPrecoService;
    private final CaixaService caixaService;
    private final EntregaService entregaService;
    private final ConfiguracaoIntegracaoRepository configuracaoIntegracaoRepository;
    private final ConfiguracaoIntegracaoService configuracaoIntegracaoService;

    public CargaDemonstracao(
            @Value("${agiliza.demo.senha}") String senha,
            CadastroUsuario cadastroUsuario,
            UsuarioRepository usuarioRepository,
            EstabelecimentoRepository estabelecimentoRepository,
            CaixaRepository caixaRepository,
            EntregaRepository entregaRepository,
            EstabelecimentoService estabelecimentoService,
            UsuarioService usuarioService,
            MotoboyService motoboyService,
            DistanciaPrecoService distanciaPrecoService,
            CaixaService caixaService,
            EntregaService entregaService,
            ConfiguracaoIntegracaoRepository configuracaoIntegracaoRepository,
            ConfiguracaoIntegracaoService configuracaoIntegracaoService) {
        this.senha = senha;
        this.cadastroUsuario = cadastroUsuario;
        this.usuarioRepository = usuarioRepository;
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.caixaRepository = caixaRepository;
        this.entregaRepository = entregaRepository;
        this.estabelecimentoService = estabelecimentoService;
        this.usuarioService = usuarioService;
        this.motoboyService = motoboyService;
        this.distanciaPrecoService = distanciaPrecoService;
        this.caixaService = caixaService;
        this.entregaService = entregaService;
        this.configuracaoIntegracaoRepository = configuracaoIntegracaoRepository;
        this.configuracaoIntegracaoService = configuracaoIntegracaoService;
    }

    @Override
    public void run(ApplicationArguments args) {
        executar();
    }

    /** Cria o que faltar. A senha nunca vai para o log (Constituicao 6.2). */
    public void executar() {
        log.warn("Carga de demonstração ligada (SÓ DESENVOLVIMENTO, D009). Desligue com AGILIZA_DEMO=false.");
        criarAdminGeral();
        Estabelecimento loja = lojaDemo();
        UUID lojaId = loja.getId();
        criarOperador(lojaId);
        criarEntregador(lojaId, "Carlos Entregador", EMAIL_ENTREGADOR_1, "52998224725", "54999990001", "IXZ1A23");
        criarEntregador(lojaId, "Daniela Entregadora", EMAIL_ENTREGADOR_2, "11144477735", "54999990002", "JAB2C34");
        criarFaixas(lojaId);
        abrirCaixaDoDia(lojaId);
        criarEntregas(lojaId);
        criarIntegracaoSimulador(lojaId);
        log.info("Carga de demonstração pronta: loja {} ({}); usuários {}, {}, {}, {}, {} "
                        + "(senha de desenvolvimento: ver README do backend).",
                loja.getNomeFantasia(), lojaId, EMAIL_ADMIN_GERAL, EMAIL_ADMIN_LOJA, EMAIL_OPERADOR,
                EMAIL_ENTREGADOR_1, EMAIL_ENTREGADOR_2);
    }

    private void criarAdminGeral() {
        if (!usuarioRepository.existsByLoginEmail(EMAIL_ADMIN_GERAL)) {
            cadastroUsuario.criar(null, "Administrador da Plataforma", EMAIL_ADMIN_GERAL, senha,
                    PerfilUsuario.ADMIN_GERAL);
        }
    }

    private Estabelecimento lojaDemo() {
        return estabelecimentoRepository.findByCnpj(CNPJ_LOJA).orElseGet(() -> {
            UUID id = estabelecimentoService.criar(new CriarEstabelecimentoRequest(
                    "Pizzaria Demonstração Ltda",
                    "Pizzaria Demo",
                    CNPJ_LOJA,
                    "5433110000",
                    "Av. Brasil Centro, 500 - Centro, Passo Fundo/RS",
                    new CoordenadaDto(LATITUDE_LOJA, LONGITUDE_LOJA),
                    (short) 5,
                    new BigDecimal("20.00"),
                    new AdministradorInicialRequest("Administrador da Loja", EMAIL_ADMIN_LOJA, senha)))
                    .id();
            return estabelecimentoRepository.findById(id).orElseThrow();
        });
    }

    private void criarOperador(UUID lojaId) {
        if (!usuarioRepository.existsByLoginEmail(EMAIL_OPERADOR)) {
            usuarioService.criar(lojaId, new CriarUsuarioRequest(
                    "Operador de Logística", EMAIL_OPERADOR, senha, PerfilUsuario.OPERADOR));
        }
    }

    private void criarEntregador(UUID lojaId, String nome, String email, String cpf, String telefone, String placa) {
        if (!usuarioRepository.existsByLoginEmail(email)) {
            motoboyService.criar(lojaId, new CriarMotoboyRequest(nome, email, senha, cpf, telefone, placa));
        }
    }

    /** Faixas contiguas a partir de 0 km (exemplo do DVP 1.5.5). */
    private void criarFaixas(UUID lojaId) {
        if (!distanciaPrecoService.listarFaixasAtivas(lojaId).isEmpty()) {
            return;
        }
        LocalDate hoje = LocalDate.now();
        distanciaPrecoService.substituirTabela(lojaId, List.of(
                new SalvarDistanciaPrecoRequest(BigDecimal.ZERO, new BigDecimal("3.00"), new BigDecimal("9.00"), hoje),
                new SalvarDistanciaPrecoRequest(new BigDecimal("3.00"), new BigDecimal("5.00"), new BigDecimal("12.00"), hoje),
                new SalvarDistanciaPrecoRequest(new BigDecimal("5.00"), new BigDecimal("8.00"), new BigDecimal("15.00"), hoje)));
    }

    private void abrirCaixaDoDia(UUID lojaId) {
        LocalDate hoje = LocalDate.now();
        if (!caixaRepository.existsByEstabelecimentoIdAndDataReferencia(lojaId, hoje)) {
            caixaService.abrirCaixa(lojaId, new AbrirCaixaRequest(hoje));
        }
    }

    /**
     * Integracao SIMULADOR inativa (T011): o admin da loja a liga com
     * PUT /api/integracoes e "ativo": true para comecar a apresentacao.
     */
    private void criarIntegracaoSimulador(UUID lojaId) {
        if (configuracaoIntegracaoRepository.findByEstabelecimentoIdAndProvedor(lojaId, Provedores.SIMULADOR)
                .isEmpty()) {
            configuracaoIntegracaoService.salvar(lojaId,
                    new SalvarConfiguracaoIntegracaoRequest(Provedores.SIMULADOR, MERCHANT_SIMULADOR, false));
        }
    }

    /** Tres entregas avulsas so na primeira carga da loja. */
    private void criarEntregas(UUID lojaId) {
        if (entregaRepository.existsByEstabelecimentoId(lojaId)) {
            return;
        }
        entregaService.criarAvulsa(lojaId, new CriarEntregaAvulsaRequest(
                "Ana Souza", "54991110001", "Rua Morom, 1200 - Centro, Passo Fundo/RS", "Centro", "99010-031",
                "Apto 302", "Em frente à farmácia",
                new CoordenadaDto(new BigDecimal("-28.2560000"), new BigDecimal("-52.4010000")),
                new BigDecimal("58.90"), FormaPagamento.PIX, true, null, "2 pizzas grandes"));
        entregaService.criarAvulsa(lojaId, new CriarEntregaAvulsaRequest(
                "Bruno Lima", "54991110002", "Rua Uruguai, 300 - Boqueirão, Passo Fundo/RS", "Boqueirão", "99025-000",
                null, "Casa de muro azul",
                new CoordenadaDto(new BigDecimal("-28.2800000"), new BigDecimal("-52.3900000")),
                new BigDecimal("72.50"), FormaPagamento.DINHEIRO, false, new BigDecimal("100.00"), "Levar troco"));
        entregaService.criarAvulsa(lojaId, new CriarEntregaAvulsaRequest(
                "Carla Mendes", "54991110003", "Rua Paissandu, 800 - Vila Rodrigues, Passo Fundo/RS",
                "Vila Rodrigues", "99070-000", "Casa 2", null,
                new CoordenadaDto(new BigDecimal("-28.2350000"), new BigDecimal("-52.4400000")),
                new BigDecimal("45.00"), FormaPagamento.CREDITO, false, null, "Maquininha de cartão"));
    }
}
