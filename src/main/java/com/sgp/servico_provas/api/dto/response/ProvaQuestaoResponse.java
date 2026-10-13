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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProvaQuestaoResponse {

    private Long id;

    private Long questaoId;
}
