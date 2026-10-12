/*
 * SGP - servico-provas
 */
package com.sgp.servico_provas.config.internationalization;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 *
 * @author diego-casemiro
 */
// Declarado à mão porque o Spring Boot só configura o MessageSource sozinho quando existe um
// i18n/messages.properties base; aqui só existe o i18n/messages_pt_BR.properties
@Configuration
public class InternationalizationConfig {

    private static final Locale DEFAULT_LOCALE = Locale.of("pt", "BR");

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("i18n/messages");
        messageSource.setDefaultEncoding("UTF-8");
        // Idiomas sem arquivo próprio caem no pt_BR, e não no idioma do sistema operacional
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setDefaultLocale(DEFAULT_LOCALE);
        return messageSource;
    }
}
