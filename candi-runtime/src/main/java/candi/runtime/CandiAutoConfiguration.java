package candi.runtime;

import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * Auto-configuration that enables Candi runtime components.
 * Scans the candi.runtime package for PageRegistry, HandlerMapping,
 * HandlerAdapter, and RequestContext beans.
 *
 * Triggers PageRegistry.scanForPages() after context refresh so all
 * page beans are discovered.
 */
@Configuration
@ComponentScan(basePackages = "candi.runtime")
public class CandiAutoConfiguration {

    @Bean
    ApplicationListener<ContextRefreshedEvent> candiPageScanner(PageRegistry pageRegistry) {
        return event -> pageRegistry.scanForPages();
    }
}
