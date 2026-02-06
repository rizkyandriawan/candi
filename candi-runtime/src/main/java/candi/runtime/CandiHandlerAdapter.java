package candi.runtime;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.util.Set;

/**
 * Spring HandlerAdapter that orchestrates the Candi page request lifecycle:
 * 1. Get request-scoped CandiPage bean from Spring DI
 * 2. page.init()
 * 3. Check HTTP method → handleAction() for non-GET
 * 4. Render full page or fragment
 */
@Component
public class CandiHandlerAdapter implements HandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(CandiHandlerAdapter.class);
    private static final Set<String> RENDER_METHODS = Set.of("GET", "HEAD");

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private PageRegistry pageRegistry;

    @Override
    public boolean supports(Object handler) {
        return handler instanceof CandiHandlerMapping.CandiPageHandler;
    }

    @Override
    public ModelAndView handle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        CandiHandlerMapping.CandiPageHandler pageHandler = (CandiHandlerMapping.CandiPageHandler) handler;
        String beanName = pageHandler.beanName();
        String method = request.getMethod();

        // 0. Check if HTTP method is allowed for this route
        Set<String> allowedMethods = pageRegistry.getAllowedMethods(beanName);
        if (!allowedMethods.contains(method)) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return null;
        }

        // 1. Get request-scoped page bean
        CandiPage page = applicationContext.getBean(beanName, CandiPage.class);

        // 2. Run @init
        page.init();

        // 3. Handle action for non-GET/HEAD methods
        if (!RENDER_METHODS.contains(method)) {
            ActionResult result = page.handleAction(method);

            switch (result) {
                case ActionResult.Redirect redirect -> {
                    response.sendRedirect(redirect.url());
                    return null;
                }
                case ActionResult.MethodNotAllowed ignored -> {
                    response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                    return null;
                }
                case ActionResult.Render ignored -> {
                    // Fall through to render
                }
            }
        }

        // 4. Check for fragment request
        String fragmentName = getFragmentName(request);

        // 5. Render
        HtmlOutput out = new HtmlOutput();
        if (fragmentName != null) {
            try {
                page.renderFragment(fragmentName, out);
            } catch (FragmentNotFoundException e) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Fragment not found: " + fragmentName);
                return null;
            }
        } else {
            page.render(out);
        }

        // 6. Write response
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(out.toHtml());

        return null;
    }

    @Override
    public long getLastModified(HttpServletRequest request, Object handler) {
        return -1;
    }

    /**
     * Detect fragment request from HX-Fragment header or _fragment query parameter.
     */
    private String getFragmentName(HttpServletRequest request) {
        // HTMX fragment header
        String header = request.getHeader("HX-Fragment");
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        // Fallback: query parameter
        String param = request.getParameter("_fragment");
        if (param != null && !param.isBlank()) {
            return param.trim();
        }
        return null;
    }
}
