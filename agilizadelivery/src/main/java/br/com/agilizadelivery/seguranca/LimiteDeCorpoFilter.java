package br.com.agilizadelivery.seguranca;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

import br.com.agilizadelivery.exception.CorpoGrandeDemaisException;
import br.com.agilizadelivery.exception.handler.RespostaDeErro;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Limita o corpo das requisicoes: o Tomcat so limita formularios, e um JSON
 * gigante esgota a memoria (o que derruba o H2, incidente de 17/11).
 *
 * Roda depois da seguranca: sem token a resposta continua 401, e o 413 sai
 * com os cabecalhos do CORS. Corpo sem Content-Length e contado na leitura.
 *
 * PROVISORIO (T014): 1 MB, muito acima do maior corpo da API.
 */
@Component
public class LimiteDeCorpoFilter extends OncePerRequestFilter {

    private final long limite;
    private final ObjectMapper json;

    public LimiteDeCorpoFilter(
            @Value("${agiliza.http.tamanho-maximo-corpo:1MB}") DataSize limite,
            ObjectMapper json) {
        this.limite = limite.toBytes();
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long declarado = request.getContentLengthLong();
        if (declarado > limite) {
            RespostaDeErro.escrever(json, response,
                    RespostaDeErro.corpoPadrao(HttpStatus.CONTENT_TOO_LARGE, request.getRequestURI()));
            return;
        }
        // com tamanho declarado, o proprio Tomcat nao le alem dele
        chain.doFilter(declarado < 0 ? new RequisicaoLimitada(request, limite) : request, response);
    }

    private static final class RequisicaoLimitada extends HttpServletRequestWrapper {

        private final long limite;
        private ServletInputStream entrada;

        RequisicaoLimitada(HttpServletRequest request, long limite) {
            super(request);
            this.limite = limite;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (entrada == null) {
                entrada = new EntradaLimitada(super.getInputStream(), limite);
            }
            return entrada;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            Charset charset = getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class EntradaLimitada extends ServletInputStream {

        private final ServletInputStream origem;
        private final long limite;
        private long lidos;

        EntradaLimitada(ServletInputStream origem, long limite) {
            this.origem = origem;
            this.limite = limite;
        }

        @Override
        public int read() throws IOException {
            int valor = origem.read();
            if (valor >= 0) {
                contar(1);
            }
            return valor;
        }

        @Override
        public int read(byte[] destino, int inicio, int tamanho) throws IOException {
            int quantidade = origem.read(destino, inicio, tamanho);
            if (quantidade > 0) {
                contar(quantidade);
            }
            return quantidade;
        }

        private void contar(int quantidade) {
            lidos += quantidade;
            if (lidos > limite) {
                throw new CorpoGrandeDemaisException();
            }
        }

        @Override
        public boolean isFinished() {
            return origem.isFinished();
        }

        @Override
        public boolean isReady() {
            return origem.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            origem.setReadListener(listener);
        }
    }
}
