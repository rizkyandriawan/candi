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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

/**
 * Spring HandlerAdapter that orchestrates the Candi page request lifecycle:
 * 1. Get request-scoped CandiPage bean from Spring DI
 * 2. page.init()
 * 3. Check HTTP method → invoke @Post/@Delete/etc annotated method via reflection
 * 4. Render full page
 */
@Component
public class CandiHandlerAdapter implements HandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(CandiHandlerAdapter.class);
    private static final Set<String> RENDER_METHODS = Set.of("GET", "HEAD");

    private static final Map<String, Class<? extends Annotation>> METHOD_ANNOTATIONS = Map.of(
            "POST", Post.class,
            "PUT", Put.class,
            "DELETE", Delete.class,
            "PATCH", Patch.class
    );

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

        // 2. Run init() — shared setup, always runs
        page.init();

        // 3. Handle action for non-GET/HEAD methods
        if (!RENDER_METHODS.contains(method)) {
            // Call onPost() for POST requests before action dispatch
            if ("POST".equals(method)) {
                page.onPost();
            }

            ActionResult result = invokeAction(page, method);

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

        // 4. onGet() — data loading before render (skipped on redirect)
        page.onGet();

        // 5. Render
        HtmlOutput out = new HtmlOutput();
        page.render(out);

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
     * Find and invoke the action method annotated with the matching HTTP method annotation.
     * E.g. for POST, looks for a method annotated with @Post.
     */
    private ActionResult invokeAction(CandiPage page, String httpMethod) {
        Class<? extends Annotation> annotationClass = METHOD_ANNOTATIONS.get(httpMethod);
        if (annotationClass == null) {
            return ActionResult.methodNotAllowed();
        }

        for (Method m : page.getClass().getDeclaredMethods()) {
            if (m.isAnnotationPresent(annotationClass)) {
                try {
                    m.setAccessible(true);
                    Object result = m.invoke(page);
                    if (result instanceof ActionResult actionResult) {
                        return actionResult;
                    }
                    // If the method returns void or non-ActionResult, fall through to render
                    return ActionResult.render();
                } catch (Exception e) {
                    log.error("Error invoking action method {} on {}", m.getName(), page.getClass().getName(), e);
                    throw new RuntimeException("Action method invocation failed", e);
                }
            }
        }

        return ActionResult.methodNotAllowed();
    }
}
