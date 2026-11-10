package br.com.agilizadelivery.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.agilizadelivery.entity.model.Caixa;
import br.com.agilizadelivery.entity.model.DistanciaPreco;
import br.com.agilizadelivery.entity.model.Entrega;
import br.com.agilizadelivery.entity.model.Estabelecimento;
import br.com.agilizadelivery.entity.model.LoteEntrega;
import br.com.agilizadelivery.entity.model.Motoboy;
import br.com.agilizadelivery.entity.model.Recibo;
import br.com.agilizadelivery.entity.model.Usuario;
import br.com.agilizadelivery.entity.model.embeddable.Coordenada;
import br.com.agilizadelivery.entity.model.enums.PerfilUsuario;
import br.com.agilizadelivery.entity.model.enums.StatusCadastroMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusCaixa;
import br.com.agilizadelivery.entity.model.enums.StatusDisponibilidadeMotoboy;
import br.com.agilizadelivery.entity.model.enums.StatusEntrega;
import br.com.agilizadelivery.entity.model.enums.StatusEstabelecimento;
import br.com.agilizadelivery.entity.model.enums.StatusLote;
import br.com.agilizadelivery.entity.model.enums.StatusRecibo;

/** Montagem das entidades usadas nos testes da camada de servico. */
public final class Fixtures {

    private Fixtures() {
    }

    public static BigDecimal reais(String valor) {
        return new BigDecimal(valor);
    }

    public static Estabelecimento estabelecimento() {
        Estabelecimento estabelecimento = new Estabelecimento();
        estabelecimento.setId(UUID.randomUUID());
        estabelecimento.setRazaoSocial("Forno Nobre Ltda");
        estabelecimento.setNomeFantasia("Pizzaria Forno Nobre");
        estabelecimento.setCnpj("11222333000181");
        estabelecimento.setEnderecoCompleto("Rua das Flores, 100 - Centro");
        estabelecimento.setCoordenada(new Coordenada(reais("-23.5505200"), reais("-46.6333080")));
        estabelecimento.setStatus(StatusEstabelecimento.ATIVO);
        estabelecimento.setMaxPedidosPorLote((short) 5);
        estabelecimento.setValorRepasseForaDeArea(reais("15.00"));
        return estabelecimento;
    }

    public static Usuario usuario(Estabelecimento estabelecimento, PerfilUsuario perfil) {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setEstabelecimento(estabelecimento);
        usuario.setNome("Usuário " + perfil);
        usuario.setLoginEmail(perfil.name().toLowerCase() + "@forno.com");
        usuario.setSenhaHash("hash");
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);
        return usuario;
    }

    public static Motoboy motoboy(Estabelecimento estabelecimento) {
        Motoboy motoboy = new Motoboy();
        motoboy.setId(UUID.randomUUID());
        Usuario usuario = usuario(estabelecimento, PerfilUsuario.ENTREGADOR);
        usuario.setNome("João");
        motoboy.setUsuario(usuario);
        motoboy.setCpfCriptografado("cifrado");
        motoboy.setCpfHash("hash");
        motoboy.setTelefone("11999990000");
        motoboy.setStatusCadastro(StatusCadastroMotoboy.ATIVO);
        motoboy.setStatusDisponibilidade(StatusDisponibilidadeMotoboy.ONLINE);
        return motoboy;
    }

    public static Entrega entrega(Estabelecimento estabelecimento, StatusEntrega status) {
        Entrega entrega = new Entrega();
        entrega.setId(UUID.randomUUID());
        entrega.setEstabelecimento(estabelecimento);
        entrega.setNomeCliente("Cliente");
        entrega.setTelefoneCliente("11988887777");
        entrega.setEnderecoCompleto("Av. Paulista, 1000");
        entrega.setCoordenada(new Coordenada(reais("-23.5614000"), reais("-46.6559000")));
        entrega.setDistanciaKm(reais("2.10"));
        entrega.setValorRepasse(reais("9.00"));
        entrega.setStatus(status);
        entrega.setPendenteRevisao(false);
        return entrega;
    }

    public static Entrega entregaFinalizada(Estabelecimento estabelecimento, LoteEntrega lote, LocalDateTime em) {
        Entrega entrega = entrega(estabelecimento, StatusEntrega.ENTREGUE);
        entrega.setLote(lote);
        entrega.setFinalizadaEm(em);
        return entrega;
    }

    public static LoteEntrega lote(Estabelecimento estabelecimento, Motoboy motoboy, StatusLote status) {
        LoteEntrega lote = new LoteEntrega();
        lote.setId(UUID.randomUUID());
        lote.setEstabelecimento(estabelecimento);
        lote.setMotoboy(motoboy);
        lote.setUsuarioDespachante(usuario(estabelecimento, PerfilUsuario.OPERADOR));
        lote.setStatus(status);
        lote.setQtdEntregas((short) 1);
        return lote;
    }

    public static Caixa caixa(Estabelecimento estabelecimento, StatusCaixa status, LocalDate data) {
        Caixa caixa = new Caixa();
        caixa.setId(UUID.randomUUID());
        caixa.setEstabelecimento(estabelecimento);
        caixa.setDataReferencia(data);
        caixa.setAbertoEm(data.atStartOfDay());
        caixa.setStatus(status);
        return caixa;
    }

    public static Recibo recibo(Estabelecimento estabelecimento, Caixa caixa, Motoboy motoboy, StatusRecibo status) {
        Recibo recibo = new Recibo();
        recibo.setId(UUID.randomUUID());
        recibo.setEstabelecimento(estabelecimento);
        recibo.setCaixa(caixa);
        recibo.setMotoboy(motoboy);
        recibo.setUsuarioGerador(usuario(estabelecimento, PerfilUsuario.OPERADOR));
        recibo.setDataReferencia(caixa.getDataReferencia());
        recibo.setPeriodoInicio(caixa.getDataReferencia().atTime(18, 0));
        recibo.setPeriodoFim(caixa.getDataReferencia().atTime(23, 59));
        recibo.setQtdEntregas((short) 1);
        recibo.setValorTotal(reais("9.00"));
        recibo.setStatus(status);
        return recibo;
    }

    public static DistanciaPreco faixa(Estabelecimento estabelecimento, String deKm, String ateKm, String valor) {
        DistanciaPreco faixa = new DistanciaPreco();
        faixa.setId(UUID.randomUUID());
        faixa.setEstabelecimento(estabelecimento);
        faixa.setDeKm(reais(deKm));
        faixa.setAteKm(reais(ateKm));
        faixa.setValorPago(reais(valor));
        faixa.setAtivo(true);
        faixa.setVigenteDesde(LocalDate.of(2026, 1, 1));
        return faixa;
    }
}
