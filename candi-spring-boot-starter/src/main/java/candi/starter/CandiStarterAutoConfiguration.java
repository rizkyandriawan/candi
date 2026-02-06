package candi.starter;

import candi.runtime.CandiAutoConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot auto-configuration for the Candi framework.
 *
 * Automatically activates when candi-runtime is on the classpath.
 * Imports the runtime's CandiAutoConfiguration (PageRegistry, HandlerMapping, etc.)
 * and binds candi.* properties.
 *
 * Users just add candi-spring-boot-starter to their pom.xml and annotate
 * their main class with @SpringBootApplication — no @Import needed.
 */
@AutoConfiguration
@ConditionalOnClass(candi.runtime.CandiPage.class)
@EnableConfigurationProperties(CandiProperties.class)
@Import(CandiAutoConfiguration.class)
public class CandiStarterAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CandiStarterAutoConfiguration.class);

    public CandiStarterAutoConfiguration(CandiProperties properties) {
        log.info("Candi framework initialized (dev={})", properties.isDev());
    }
}
