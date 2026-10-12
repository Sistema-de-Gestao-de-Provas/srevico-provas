/*
 * SGP - servico-provas
 */
package com.sgp.servico_provas.domain.repository;

import com.sgp.servico_provas.domain.entity.Prova;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 *
 * @author diego-casemiro
 */
public interface ProvaRepository extends JpaRepository<Prova, Long>, JpaSpecificationExecutor<Prova> {

    Optional<Prova> findByIdAndDeletadoEmIsNull(Long id);
}
