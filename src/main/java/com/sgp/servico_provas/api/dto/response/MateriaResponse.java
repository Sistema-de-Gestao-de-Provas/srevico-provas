/*
 * SGP - servico-provas
 */
package com.sgp.servico_provas.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 *
 * @author diego-casemiro
 */
// Resposta do GET /v1/materias/{id} do servico-usuarios
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MateriaResponse {

    private Long id;

    private String nome;
}
