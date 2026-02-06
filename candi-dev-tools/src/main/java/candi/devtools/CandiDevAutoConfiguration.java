package candi.devtools;

import candi.runtime.PageRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Auto-configuration for Candi dev tools.
 * Activated when candi.dev=true is set in application properties.
 *
 * Sets up:
 * - FileWatcher for .page.html changes
 * - IncrementalCompiler for in-memory compilation
 * - HotReloadManager to orchestrate reload cycle
 * - LiveReloadServer (SSE) for browser notification
 * - LiveReloadFilter to inject reload script into HTML
 */
@Configuration
@ConditionalOnProperty(name = "candi.dev", havingValue = "true")
public class CandiDevAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CandiDevAutoConfiguration.class);

    @Bean
    public LiveReloadServer candiLiveReloadServer() {
        return new LiveReloadServer();
    }

    @Bean
    public IncrementalCompiler candiIncrementalCompiler() {
        return new IncrementalCompiler();
    }

    @Bean
    public HotReloadManager candiHotReloadManager(
            PageRegistry pageRegistry,
            ApplicationContext applicationContext,
            IncrementalCompiler incrementalCompiler,
            LiveReloadServer liveReloadServer) {

        String packageName = applicationContext.getEnvironment()
                .getProperty("candi.package", "pages");
        String classpath = buildClasspath();

        HotReloadManager manager = new HotReloadManager(
                pageRegistry, applicationContext, incrementalCompiler,
                liveReloadServer, packageName, classpath);

        // Start file watcher
        String sourceDir = applicationContext.getEnvironment()
                .getProperty("candi.source-dir", "src/main/candi");
        Path watchPath = Paths.get(sourceDir);

        if (Files.isDirectory(watchPath)) {
            FileWatcher watcher = new FileWatcher(watchPath, manager::onFileChanged);
            Thread watcherThread = new Thread(watcher, "candi-file-watcher");
            watcherThread.setDaemon(true);
            watcherThread.start();
            log.info("Candi dev tools: watching {} for changes", watchPath);
        } else {
            log.warn("Candi dev tools: source directory not found: {}", watchPath);
        }

        return manager;
    }

    @Bean
    public RouterFunction<ServerResponse> candiLiveReloadEndpoint(LiveReloadServer liveReloadServer) {
        return RouterFunctions.route()
                .GET("/_candi/reload", request ->
                        ServerResponse.ok()
                                .contentType(MediaType.TEXT_EVENT_STREAM)
                                .body(liveReloadServer.subscribe()))
                .build();
    }

    @Bean
    public FilterRegistrationBean<LiveReloadFilter> candiLiveReloadFilter() {
        FilterRegistrationBean<LiveReloadFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new LiveReloadFilter());
        registration.addUrlPatterns("/*");
        registration.setName("candiLiveReloadFilter");
        registration.setOrder(Integer.MAX_VALUE); // Run last
        return registration;
    }

    /**
     * Build the classpath string from the current JVM's classpath.
     * In dev mode, the running JVM has all needed dependencies.
     */
    private String buildClasspath() {
        return System.getProperty("java.class.path", "");
    }
}
