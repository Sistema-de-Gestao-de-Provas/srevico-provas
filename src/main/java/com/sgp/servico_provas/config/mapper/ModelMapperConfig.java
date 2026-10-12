/*
 * SGP - servico-provas
 */
package com.sgp.servico_provas.config.mapper;

import org.modelmapper.config.Configuration.AccessLevel;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author diego-casemiro
 */
@Configuration
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();

        modelMapper.getConfiguration()
                // Usa a estratégia STRICT para evitar mapeamentos incorretos por ambiguidade de nomes
                .setMatchingStrategy(MatchingStrategies.STRICT)
                // Permite que o ModelMapper leia campos privados diretamente (evita erros se faltar getter/setter)
                .setFieldMatchingEnabled(true)
                .setFieldAccessLevel(AccessLevel.PRIVATE)
                // Ignora valores nulos na origem (útil para cenários de PATCH/atualização parcial)
                .setSkipNullEnabled(true);

        return modelMapper;
    }
}
