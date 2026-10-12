/*
 * SGP - servico-provas
 */
package com.sgp.servico_provas.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 *
 * @author diego-casemiro
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final transient Object[] args;

    protected ApiException(String messageKey, Object... args) {
        super(messageKey);
        this.args = args;
    }

    public abstract HttpStatus getStatus();
}
