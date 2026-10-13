/*
 * SGP - servico-provas
 */
package com.sgp.servico_provas.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
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
public class ProvaRequest {

    // Validado no servico-usuarios ao criar ou ao trocar a matéria da prova
    @NotNull
    private Long materiaId;

    @NotBlank
    @Size(max = 150)
    private String titulo;

    private String descricao;

    // Validadas no servico-questoes; o rascunho pode ser salvo sem questões, a publicação não
    @NotNull
    private List<@NotNull Long> questaoIds;
}
