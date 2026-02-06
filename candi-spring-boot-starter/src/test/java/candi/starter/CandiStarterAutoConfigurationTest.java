package candi.starter;

import candi.runtime.CandiHandlerAdapter;
import candi.runtime.CandiHandlerMapping;
import candi.runtime.PageRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

class CandiStarterAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CandiStarterAutoConfiguration.class));

    @Test
    void autoConfigurationRegistersRuntimeBeans() {
        contextRunner.run(context -> {
            assertTrue(context.containsBean("pageRegistry"));
            assertTrue(context.containsBean("candiHandlerMapping"));
            assertTrue(context.containsBean("candiHandlerAdapter"));
            assertTrue(context.containsBean("requestContext"));
        });
    }

    @Test
    void pageRegistryBeanIsCreated() {
        contextRunner.run(context -> {
            PageRegistry registry = context.getBean(PageRegistry.class);
            assertNotNull(registry);
        });
    }

    @Test
    void handlerMappingBeanIsCreated() {
        contextRunner.run(context -> {
            CandiHandlerMapping mapping = context.getBean(CandiHandlerMapping.class);
            assertNotNull(mapping);
        });
    }

    @Test
    void handlerAdapterBeanIsCreated() {
        contextRunner.run(context -> {
            CandiHandlerAdapter adapter = context.getBean(CandiHandlerAdapter.class);
            assertNotNull(adapter);
        });
    }

    @Test
    void candiPropertiesAreBound() {
        contextRunner
                .withPropertyValues("candi.dev=true", "candi.source-dir=custom/src", "candi.package-name=myapp")
                .run(context -> {
                    CandiProperties props = context.getBean(CandiProperties.class);
                    assertTrue(props.isDev());
                    assertEquals("custom/src", props.getSourceDir());
                    assertEquals("myapp", props.getPackageName());
                });
    }

    @Test
    void defaultPropertiesValues() {
        contextRunner.run(context -> {
            CandiProperties props = context.getBean(CandiProperties.class);
            assertFalse(props.isDev());
            assertEquals("src/main/candi", props.getSourceDir());
            assertEquals("pages", props.getPackageName());
        });
    }
}
