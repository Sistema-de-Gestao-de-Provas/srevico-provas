/*
 * SGP - servico-provas
 */
package com.sgp.servico_provas.api.dto.response;

import com.sgp.servico_provas.domain.enums.Status;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
public class ProvaResponse {

    private Long id;

    private UUID usuarioKeycloakId;

    private Long materiaId;

    private String titulo;

    private String descricao;

    private Status status;

    private List<ProvaQuestaoResponse> questoes;

    private LocalDateTime criadoEm;

    private LocalDateTime atualizadoEm;
}
