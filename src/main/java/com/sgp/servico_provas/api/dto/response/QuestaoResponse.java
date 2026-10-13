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
// Resposta do GET /v1/questoes/{id} do servico-questoes; só os campos usados aqui
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuestaoResponse {

    private Long id;

    private Long materiaId;
}
