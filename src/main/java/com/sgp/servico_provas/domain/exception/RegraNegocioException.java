/*
 * SGP - servico-provas
 */
package com.sgp.servico_provas.domain.exception;

import org.springframework.http.HttpStatus;

/**
 *
 * @author diego-casemiro
 */
public class RegraNegocioException extends ApiException {

    public RegraNegocioException(String messageKey, Object... args) {
        super(messageKey, args);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
