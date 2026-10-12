/*
 * SGP - servico-provas
 */
package com.sgp.servico_provas.domain.repository.specification;

import com.sgp.servico_provas.domain.entity.Prova;
import com.sgp.servico_provas.domain.enums.Status;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 *
 * @author diego-casemiro
 */
public final class ProvaSpecification {

    public static final Set<String> CAMPOS_ORDENAVEIS = Set.of(
            "id", "materiaId", "titulo", "status", "criadoEm", "atualizadoEm");

    private ProvaSpecification() {
    }

    public static Specification<Prova> filtrar(Long materiaId, Status status, String titulo, UUID usuarioKeycloakId) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            predicados.add(cb.isNull(root.get("deletadoEm")));

            if (usuarioKeycloakId != null) {
                predicados.add(cb.equal(root.get("usuarioKeycloakId"), usuarioKeycloakId));
            }
            if (materiaId != null) {
                predicados.add(cb.equal(root.get("materiaId"), materiaId));
            }
            if (status != null) {
                predicados.add(cb.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(titulo)) {
                predicados.add(cb.like(cb.lower(root.get("titulo")), "%" + titulo.trim().toLowerCase() + "%"));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }
}
