package br.com.agilizadelivery.exception.handler;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.apache.coyote.ActionCode;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Pagina de erro do proprio Tomcat, para o que ele recusa antes de chegar
 * a aplicacao (URI invalida, %2F no caminho): ApiErrorResponse em vez de HTML.
 */
@Slf4j
public class ValvulaDeErroJson extends ErrorReportValve {

    private final ObjectMapper json;

    public ValvulaDeErroJson(ObjectMapper json) {
        this.json = json;
    }

    @Override
    protected void report(Request request, Response response, Throwable falha) {
        int status = response.getStatus();
        if (status < 400 || status > 599 || response.getContentWritten() > 0 || !response.setErrorReported()) {
            return;
        }
        AtomicBoolean escritaPermitida = new AtomicBoolean(false);
        response.getCoyoteResponse().action(ActionCode.IS_IO_ALLOWED, escritaPermitida);
        if (!escritaPermitida.get()) {
            return;
        }
        try {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            Writer saida = response.getReporter();
            if (saida != null) {
                saida.write(json.writeValueAsString(
                        RespostaDeErro.corpoPadrao(HttpStatusCode.valueOf(status), request.getRequestURI())));
                response.finishResponse();
            }
        } catch (IOException e) {
            log.debug("Cliente saiu antes de receber o erro: {}", e.getMessage());
        }
    }
}
