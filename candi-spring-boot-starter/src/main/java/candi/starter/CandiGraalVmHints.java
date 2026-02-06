package candi.starter;

import candi.runtime.*;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * GraalVM native image reflection hints for Candi-generated page classes.
 *
 * Registers all classes annotated with @CandiRoute for reflection access,
 * which is needed for Spring DI and request-scoped bean creation in native image.
 *
 * Activated automatically via META-INF/spring/aot.factories or RuntimeHintsRegistrar.
 */
public class CandiGraalVmHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // Register core Candi runtime classes
        registerClass(hints, CandiPage.class);
        registerClass(hints, CandiRoute.class);
        registerClass(hints, HtmlOutput.class);
        registerClass(hints, ActionResult.class);
        registerClass(hints, ActionResult.Redirect.class);
        registerClass(hints, ActionResult.Render.class);
        registerClass(hints, ActionResult.MethodNotAllowed.class);
        registerClass(hints, RequestContext.class);
        registerClass(hints, PageRegistry.class);
        registerClass(hints, CandiHandlerMapping.class);
        registerClass(hints, CandiHandlerAdapter.class);
        registerClass(hints, FragmentNotFoundException.class);
        registerClass(hints, CandiLayout.class);
        registerClass(hints, SlotProvider.class);
        registerClass(hints, CandiComponent.class);
    }

    private void registerClass(RuntimeHints hints, Class<?> clazz) {
        hints.reflection().registerType(clazz,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS);
    }

    /**
     * Register all discovered @CandiRoute page classes for reflection.
     * Called at build time during AOT processing.
     */
    public static void registerPageClasses(RuntimeHints hints, ApplicationContext context) {
        if (!(context instanceof ConfigurableApplicationContext configCtx)) return;

        ConfigurableListableBeanFactory beanFactory = configCtx.getBeanFactory();

        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition bd = beanFactory.getBeanDefinition(beanName);
            String className = bd.getBeanClassName();
            if (className == null) continue;

            try {
                Class<?> beanClass = Class.forName(className);
                if (beanClass.isAnnotationPresent(CandiRoute.class)) {
                    hints.reflection().registerType(beanClass,
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_DECLARED_METHODS,
                            MemberCategory.INVOKE_PUBLIC_METHODS,
                            MemberCategory.DECLARED_FIELDS);
                }
            } catch (ClassNotFoundException e) {
                // Skip
            }
        }
    }
}
