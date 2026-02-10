package candi.runtime;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring HandlerMapping that resolves requests to Candi page beans via PageRegistry.
 * Returns the bean name as the handler, with path variables stored as request attributes.
 */
@Component
public class CandiHandlerMapping extends AbstractHandlerMapping {

    @Autowired
    private PageRegistry pageRegistry;

    private final List<HandlerInterceptor> candiInterceptors = new ArrayList<>();

    public CandiHandlerMapping() {
        // Run after Spring's default handler mappings
        setOrder(Ordered.LOWEST_PRECEDENCE - 1);
    }

    /**
     * Add a HandlerInterceptor to this mapping's interceptor chain.
     * Called by auto-configurations (e.g. candi-auth-core) to register
     * interceptors that should apply to Candi page requests.
     */
    public void addCandiInterceptor(HandlerInterceptor interceptor) {
        candiInterceptors.add(interceptor);
    }

    @Override
    protected Object getHandlerInternal(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Match by path only — method checking is done by the adapter
        // so we can produce proper 405 responses instead of 404
        PageRegistry.ResolveResult result = pageRegistry.resolveByPath(path);
        if (result == null) {
            return null;
        }

        // Store path variables as request attribute (used by RequestContext.path())
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, result.pathVariables());

        return new CandiPageHandler(result.beanName());
    }

    @Override
    protected HandlerExecutionChain getHandlerExecutionChain(Object handler, HttpServletRequest request) {
        HandlerExecutionChain chain = super.getHandlerExecutionChain(handler, request);
        for (HandlerInterceptor interceptor : candiInterceptors) {
            chain.addInterceptor(interceptor);
        }
        return chain;
    }

    /**
     * Handler object that wraps the page bean name.
     * CandiHandlerAdapter knows how to handle this.
     */
    public record CandiPageHandler(String beanName) {}
}
