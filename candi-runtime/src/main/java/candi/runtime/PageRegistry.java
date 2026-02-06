package candi.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discovers @CandiRoute-annotated beans and resolves HTTP requests to page bean names.
 * Thread-safe — supports hot reload via unregister/register.
 */
@Component
public class PageRegistry {

    private static final Logger log = LoggerFactory.getLogger(PageRegistry.class);

    private final PathPatternParser patternParser = new PathPatternParser();
    private final ConcurrentHashMap<String, RouteEntry> routes = new ConcurrentHashMap<>();

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * A registered route entry: pattern, bean name, and allowed methods.
     */
    public record RouteEntry(PathPattern pattern, String beanName, Set<String> methods) {}

    /**
     * Result of resolving a request: matched bean name and extracted path variables.
     */
    public record ResolveResult(String beanName, Map<String, String> pathVariables) {}

    /**
     * Scan the ApplicationContext for all beans annotated with @CandiRoute.
     * Inspects bean definitions (class metadata) without instantiating request-scoped beans.
     * Called automatically by CandiAutoConfiguration after context refresh.
     */
    public void scanForPages() {
        ConfigurableListableBeanFactory beanFactory =
                ((ConfigurableApplicationContext) applicationContext).getBeanFactory();

        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            BeanDefinition bd = beanFactory.getBeanDefinition(beanName);
            String className = bd.getBeanClassName();
            if (className == null) continue;

            try {
                Class<?> beanClass = Class.forName(className);
                CandiRoute route = beanClass.getAnnotation(CandiRoute.class);
                if (route != null) {
                    register(beanName, route.path(), Set.of(route.methods()));
                }
            } catch (ClassNotFoundException e) {
                // Not a page class, skip
            }
        }
        log.info("Candi PageRegistry: {} page(s) registered", routes.size());
    }

    /**
     * Register a route.
     */
    public void register(String beanName, String path, Set<String> methods) {
        PathPattern pattern = patternParser.parse(path);
        routes.put(beanName, new RouteEntry(pattern, beanName, methods));
        log.debug("Registered page: {} -> {} ({})", path, beanName, methods);
    }

    /**
     * Unregister a route by bean name.
     */
    public void unregister(String beanName) {
        RouteEntry removed = routes.remove(beanName);
        if (removed != null) {
            log.debug("Unregistered page: {}", beanName);
        }
    }

    /**
     * Resolve an HTTP request to a page bean, filtering by method.
     *
     * @param requestPath the request URI path (e.g. "/post/42")
     * @param method      the HTTP method (e.g. "GET", "POST")
     * @return resolve result with bean name and path variables, or null if no match
     */
    public ResolveResult resolve(String requestPath, String method) {
        org.springframework.http.server.PathContainer pathContainer =
                org.springframework.http.server.PathContainer.parsePath(requestPath);

        ResolveResult bestMatch = null;
        PathPattern bestPattern = null;

        for (RouteEntry entry : routes.values()) {
            if (!entry.methods().contains(method)) {
                continue;
            }
            PathPattern.PathMatchInfo matchInfo = entry.pattern().matchAndExtract(pathContainer);
            if (matchInfo != null) {
                if (bestPattern == null || entry.pattern().compareTo(bestPattern) < 0) {
                    bestPattern = entry.pattern();
                    bestMatch = new ResolveResult(entry.beanName, matchInfo.getUriVariables());
                }
            }
        }

        return bestMatch;
    }

    /**
     * Resolve a request to a page bean by path only (ignoring HTTP method).
     * Used by HandlerMapping so the adapter can produce proper 405 responses.
     */
    public ResolveResult resolveByPath(String requestPath) {
        org.springframework.http.server.PathContainer pathContainer =
                org.springframework.http.server.PathContainer.parsePath(requestPath);

        ResolveResult bestMatch = null;
        PathPattern bestPattern = null;

        for (RouteEntry entry : routes.values()) {
            PathPattern.PathMatchInfo matchInfo = entry.pattern().matchAndExtract(pathContainer);
            if (matchInfo != null) {
                if (bestPattern == null || entry.pattern().compareTo(bestPattern) < 0) {
                    bestPattern = entry.pattern();
                    bestMatch = new ResolveResult(entry.beanName, matchInfo.getUriVariables());
                }
            }
        }

        return bestMatch;
    }

    /**
     * Get the allowed methods for a given bean name.
     */
    public Set<String> getAllowedMethods(String beanName) {
        RouteEntry entry = routes.get(beanName);
        return entry != null ? entry.methods() : Set.of();
    }

    /**
     * Get the number of registered routes.
     */
    public int size() {
        return routes.size();
    }

    /**
     * Clear all registered routes. Used for testing and hot reload.
     */
    public void clear() {
        routes.clear();
    }
}
