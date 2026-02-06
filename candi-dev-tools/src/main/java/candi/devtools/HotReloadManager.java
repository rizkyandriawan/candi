package candi.devtools;

import candi.runtime.CandiPage;
import candi.runtime.CandiRoute;
import candi.runtime.PageRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates the hot reload cycle:
 * 1. Compile .page.html → Java source → bytecode
 * 2. Load bytecode via PageClassLoader
 * 3. Update PageRegistry (unregister old, register new)
 * 4. Register new Spring bean definition
 * 5. Notify LiveReloadServer
 */
public class HotReloadManager {

    private static final Logger log = LoggerFactory.getLogger(HotReloadManager.class);

    private final PageRegistry pageRegistry;
    private final ApplicationContext applicationContext;
    private final IncrementalCompiler incrementalCompiler;
    private final LiveReloadServer liveReloadServer;
    private final String packageName;
    private final String classpath;

    /** Tracks which bean names are managed by hot reload, mapped by file path */
    private final Map<String, String> fileToBeanName = new ConcurrentHashMap<>();

    private volatile PageClassLoader currentClassLoader;

    public HotReloadManager(PageRegistry pageRegistry,
                            ApplicationContext applicationContext,
                            IncrementalCompiler incrementalCompiler,
                            LiveReloadServer liveReloadServer,
                            String packageName,
                            String classpath) {
        this.pageRegistry = pageRegistry;
        this.applicationContext = applicationContext;
        this.incrementalCompiler = incrementalCompiler;
        this.liveReloadServer = liveReloadServer;
        this.packageName = packageName;
        this.classpath = classpath;
        this.currentClassLoader = new PageClassLoader(getClass().getClassLoader());
    }

    /**
     * Handle a file change event. Called by FileWatcher.
     */
    public void onFileChanged(Path changedFile) {
        long start = System.currentTimeMillis();
        String fileName = changedFile.getFileName().toString();
        log.info("Hot reload: {} changed", fileName);

        // Step 1: Compile to bytecode
        IncrementalCompiler.CompileResult result =
                incrementalCompiler.compile(changedFile, packageName, classpath);

        if (result.hasErrors()) {
            String errorMsg = String.join("\n", result.errors());
            log.error("Hot reload compilation failed:\n{}", errorMsg);
            liveReloadServer.notifyReload(errorMsg);
            return;
        }

        try {
            // Step 2: Create new classloader and load the class
            PageClassLoader newClassLoader = new PageClassLoader(getClass().getClassLoader());

            // Copy existing classes (except the one being reloaded)
            // We use a fresh classloader each time for clean state
            newClassLoader.addClass(result.fqcn(), result.bytecode());

            Class<?> pageClass = newClassLoader.loadClass(result.fqcn());

            // Verify it implements CandiPage
            if (!CandiPage.class.isAssignableFrom(pageClass)) {
                log.error("Hot reload: {} does not implement CandiPage", result.fqcn());
                liveReloadServer.notifyReload("Class does not implement CandiPage: " + result.fqcn());
                return;
            }

            // Step 3: Get route annotation
            CandiRoute route = pageClass.getAnnotation(CandiRoute.class);
            if (route == null) {
                log.warn("Hot reload: {} has no @CandiRoute annotation", result.fqcn());
                liveReloadServer.notifyReload("Missing @CandiRoute on: " + result.fqcn());
                return;
            }

            // Step 4: Unregister old bean (if exists)
            String fileKey = changedFile.toAbsolutePath().toString();
            String oldBeanName = fileToBeanName.get(fileKey);
            if (oldBeanName != null) {
                pageRegistry.unregister(oldBeanName);
                removeBeanDefinition(oldBeanName);
                log.debug("Unregistered old bean: {}", oldBeanName);
            }

            // Step 5: Register new bean definition
            String beanName = result.className();
            registerBeanDefinition(beanName, pageClass);
            fileToBeanName.put(fileKey, beanName);

            // Step 6: Register route in PageRegistry
            pageRegistry.register(beanName, route.path(), Set.of(route.methods()));

            // Step 7: Update classloader reference
            this.currentClassLoader = newClassLoader;

            long elapsed = System.currentTimeMillis() - start;
            log.info("Hot reload: {} completed in {}ms", fileName, elapsed);

            // Step 8: Notify browser
            liveReloadServer.notifyReload();

        } catch (Exception e) {
            log.error("Hot reload failed for {}", fileName, e);
            liveReloadServer.notifyReload("Hot reload error: " + e.getMessage());
        }
    }

    private void registerBeanDefinition(String beanName, Class<?> pageClass) {
        if (applicationContext instanceof ConfigurableApplicationContext configCtx) {
            BeanDefinitionRegistry registry =
                    (BeanDefinitionRegistry) configCtx.getBeanFactory();

            GenericBeanDefinition bd = new GenericBeanDefinition();
            bd.setBeanClass(pageClass);
            bd.setScope(WebApplicationContext.SCOPE_REQUEST);
            bd.setAutowireMode(GenericBeanDefinition.AUTOWIRE_BY_TYPE);

            registry.registerBeanDefinition(beanName, bd);
            log.debug("Registered bean definition: {} -> {}", beanName, pageClass.getName());
        }
    }

    private void removeBeanDefinition(String beanName) {
        if (applicationContext instanceof ConfigurableApplicationContext configCtx) {
            BeanDefinitionRegistry registry =
                    (BeanDefinitionRegistry) configCtx.getBeanFactory();
            if (registry.containsBeanDefinition(beanName)) {
                registry.removeBeanDefinition(beanName);
            }
        }
    }

    /**
     * Get the current page classloader.
     */
    public PageClassLoader getCurrentClassLoader() {
        return currentClassLoader;
    }
}
