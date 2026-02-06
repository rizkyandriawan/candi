package candi.runtime;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;

import java.util.Map;

/**
 * Spring HandlerMapping that resolves requests to Candi page beans via PageRegistry.
 * Returns the bean name as the handler, with path variables stored as request attributes.
 */
@Component
public class CandiHandlerMapping extends AbstractHandlerMapping {

    @Autowired
    private PageRegistry pageRegistry;

    public CandiHandlerMapping() {
        // Run after Spring's default handler mappings
        setOrder(Ordered.LOWEST_PRECEDENCE - 1);
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

    /**
     * Handler object that wraps the page bean name.
     * CandiHandlerAdapter knows how to handle this.
     */
    public record CandiPageHandler(String beanName) {}
}
