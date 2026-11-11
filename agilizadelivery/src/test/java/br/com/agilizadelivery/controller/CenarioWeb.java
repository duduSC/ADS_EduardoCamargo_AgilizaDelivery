package br.com.agilizadelivery.controller;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import br.com.agilizadelivery.dto.response.UsuarioAutenticadoResponse;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCadastroMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import br.com.agilizadelivery.repository.EntregaRepository;
import br.com.agilizadelivery.repository.EstabelecimentoRepository;
import br.com.agilizadelivery.repository.MotoboyRepository;
import br.com.agilizadelivery.repository.UsuarioRepository;
import br.com.agilizadelivery.seguranca.jwt.ServicoJwt;
import br.com.agilizadelivery.service.CanalAcesso;

/**
 * Monta dados persistidos (H2) e tokens para os testes de controller.
 * Cada chamada gera CNPJ, e-mail e CPF unicos, para que os testes nao
 * dependam de ordem nem de limpeza do banco.
 */
@Component
public class CenarioWeb {

    /** Senha usada so nos testes; o banco guarda o hash BCrypt. */
    public static final String SENHA = "senha-de-teste-123";

    private final EstabelecimentoRepository estabelecimentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MotoboyRepository motoboyRepository;
    private final EntregaRepository entregaRepository;
    private final PasswordEncoder passwordEncoder;
    private final ServicoJwt servicoJwt;
    private String hashSenha;

    public CenarioWeb(
            EstabelecimentoRepository estabelecimentoRepository,
            UsuarioRepository usuarioRepository,
            MotoboyRepository motoboyRepository,
            EntregaRepository entregaRepository,
            PasswordEncoder passwordEncoder,
            ServicoJwt servicoJwt) {
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.usuarioRepository = usuarioRepository;
        this.motoboyRepository = motoboyRepository;
        this.entregaRepository = entregaRepository;
        this.passwordEncoder = passwordEncoder;
        this.servicoJwt = servicoJwt;
    }

    private static String digitos(int quantidade) {
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < quantidade; i++) {
            texto.append(ThreadLocalRandom.current().nextInt(10));
        }
        return texto.toString();
    }

    /** Gera um CNPJ valido (14 digitos com dv correto) para testes. */
    private static String cnpjValido() {
        int[] d = new int[14];
        for (int i = 0; i < 8; i++) d[i] = ThreadLocalRandom.current().nextInt(10);
        d[8] = 0; d[9] = 0; d[10] = 0; d[11] = 1; // filial 0001
        int[] p1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int soma = 0;
        for (int i = 0; i < 12; i++) soma += d[i] * p1[i];
        int resto = soma % 11;
        d[12] = resto < 2 ? 0 : 11 - resto;
        int[] p2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        soma = 0;
        for (int i = 0; i < 13; i++) soma += d[i] * p2[i];
        resto = soma % 11;
        d[13] = resto < 2 ? 0 : 11 - resto;
        StringBuilder sb = new StringBuilder(14);
        for (int v : d) sb.append(v);
        return sb.toString();
    }

    public Estabelecimento estabelecimento() {
        return estabelecimento(StatusEstabelecimento.ATIVO, (short) 5);
    }

    public Estabelecimento estabelecimento(StatusEstabelecimento status, short maxPedidosPorLote) {
        Estabelecimento estabelecimento = new Estabelecimento();
        estabelecimento.setRazaoSocial("Loja " + digitos(4) + " Ltda");
        estabelecimento.setNomeFantasia("Loja de teste");
        estabelecimento.setCnpj(cnpjValido());
        estabelecimento.setEnderecoCompleto("Rua das Flores, 100 - Centro");
        estabelecimento.setCoordenada(new Coordenada(new BigDecimal("-23.5505200"), new BigDecimal("-46.6333080")));
        estabelecimento.setStatus(status);
        estabelecimento.setMaxPedidosPorLote(maxPedidosPorLote);
        estabelecimento.setValorRepasseForaDeArea(new BigDecimal("15.00"));
        return estabelecimentoRepository.save(estabelecimento);
    }

    public Usuario usuario(Estabelecimento estabelecimento, PerfilUsuario perfil) {
        if (hashSenha == null) {
            hashSenha = passwordEncoder.encode(SENHA);
        }
        Usuario usuario = new Usuario();
        usuario.setEstabelecimento(estabelecimento);
        usuario.setNome("Usuário " + perfil);
        usuario.setLoginEmail(perfil.name().toLowerCase() + "." + UUID.randomUUID() + "@teste.com");
        usuario.setSenhaHash(hashSenha);
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);
        return usuarioRepository.save(usuario);
    }

    public Motoboy motoboy(Estabelecimento estabelecimento) {
        return motoboy(estabelecimento, StatusCadastroMotoboy.ATIVO, StatusDisponibilidadeMotoboy.ONLINE);
    }

    public Motoboy motoboy(Estabelecimento estabelecimento, StatusCadastroMotoboy cadastro,
            StatusDisponibilidadeMotoboy disponibilidade) {
        Motoboy motoboy = new Motoboy();
        motoboy.setUsuario(usuario(estabelecimento, PerfilUsuario.ENTREGADOR));
        motoboy.setCpfCriptografado("cifrado-" + digitos(11));
        motoboy.setCpfHash(digitos(64));
        motoboy.setTelefone("11999990000");
        motoboy.setStatusCadastro(cadastro);
        motoboy.setStatusDisponibilidade(disponibilidade);
        return motoboyRepository.save(motoboy);
    }

    public Entrega entrega(Estabelecimento estabelecimento, StatusEntrega status) {
        Entrega entrega = new Entrega();
        entrega.setEstabelecimento(estabelecimento);
        entrega.setNomeCliente("Cliente");
        entrega.setTelefoneCliente("11988887777");
        entrega.setEnderecoCompleto("Av. Paulista, 1000");
        entrega.setCoordenada(new Coordenada(new BigDecimal("-23.5614000"), new BigDecimal("-46.6559000")));
        entrega.setDistanciaKm(new BigDecimal("2.10"));
        entrega.setValorRepasse(new BigDecimal("9.00"));
        entrega.setStatus(status);
        entrega.setPendenteRevisao(false);
        return entregaRepository.save(entrega);
    }

    /** Token de acesso emitido como o login emitiria, para o canal do perfil. */
    public String token(Usuario usuario) {
        CanalAcesso canal = usuario.getPerfil() == PerfilUsuario.ENTREGADOR ? CanalAcesso.APP : CanalAcesso.BACKOFFICE;
        UUID estabelecimentoId = usuario.getEstabelecimento() == null ? null : usuario.getEstabelecimento().getId();
        return servicoJwt.gerarTokenAcesso(
                new UsuarioAutenticadoResponse(usuario.getId(), estabelecimentoId, null, usuario.getNome(),
                        usuario.getPerfil()),
                canal);
    }

    public String bearer(Usuario usuario) {
        return "Bearer " + token(usuario);
    }
}
