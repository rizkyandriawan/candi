package candi.runtime;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * Request-scoped context object available to pages via @inject RequestContext ctx.
 * Provides convenient access to path variables, query params, form data, and headers.
 */
@Component
@Scope(WebApplicationContext.SCOPE_REQUEST)
public class RequestContext {

    @Autowired
    private HttpServletRequest request;

    /**
     * Get a path variable value (e.g. ctx.path("id") for "/post/{id}").
     */
    public String path(String name) {
        @SuppressWarnings("unchecked")
        Map<String, String> pathVars = (Map<String, String>)
                request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVars == null) return null;
        return pathVars.get(name);
    }

    /**
     * Get a query parameter value (e.g. ctx.query("page") for "?page=2").
     */
    public String query(String name) {
        return request.getParameter(name);
    }

    /**
     * Get a form field value from POST body.
     */
    public String form(String name) {
        return request.getParameter(name);
    }

    /**
     * Get a request header value.
     */
    public String header(String name) {
        return request.getHeader(name);
    }

    /**
     * Get the HTTP method (GET, POST, etc).
     */
    public String method() {
        return request.getMethod();
    }

    /**
     * Get the request URI path.
     */
    public String uri() {
        return request.getRequestURI();
    }

    /**
     * Get the underlying HttpServletRequest.
     */
    public HttpServletRequest raw() {
        return request;
    }
}
