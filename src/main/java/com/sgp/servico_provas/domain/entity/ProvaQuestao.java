/*
 * SGP - servico-provas
 */
package com.sgp.servico_provas.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 *
 * @author diego-casemiro
 */
@Entity
@Table(name = "prova_questao")
@Getter
@Setter
@NoArgsConstructor
public class ProvaQuestao extends EntidadeBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prova_id", nullable = false)
    private Prova prova;

    @Column(name = "questao_id", nullable = false)
    private Long questaoId;
}
