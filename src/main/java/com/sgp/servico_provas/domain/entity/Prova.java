/*
 * SGP - servico-provas
 */
package com.sgp.servico_provas.domain.entity;

import com.sgp.servico_provas.domain.enums.Status;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 *
 * @author diego-casemiro
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Prova extends EntidadeBase {

    @Column(name = "usuario_keycloak_id", nullable = false, updatable = false)
    private UUID usuarioKeycloakId;

    @Column(name = "materia_id", nullable = false)
    private Long materiaId;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @OneToMany(mappedBy = "prova", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProvaQuestao> questoes = new ArrayList<>();
}
