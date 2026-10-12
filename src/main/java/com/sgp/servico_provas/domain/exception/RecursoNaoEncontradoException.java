/*
 * SGP - servico-provas
 */
package com.sgp.servico_provas.domain.exception;

import org.springframework.http.HttpStatus;

/**
 *
 * @author diego-casemiro
 */
public class RecursoNaoEncontradoException extends ApiException {

    public RecursoNaoEncontradoException(String messageKey, Object... args) {
        super(messageKey, args);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
