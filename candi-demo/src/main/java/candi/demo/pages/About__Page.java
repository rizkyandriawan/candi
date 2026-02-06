package candi.demo.pages;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import candi.runtime.*;

/**
 * Hand-compiled page simulating compiler output for:
 *
 * @page "/about"
 * @layout "base"
 *
 * @slot title { About Us }
 * @slot content {
 *   <h1>About Candi</h1>
 *   <p>An HTML-first framework for Spring Boot.</p>
 * }
 */
@Component
@Scope(WebApplicationContext.SCOPE_REQUEST)
@CandiRoute(path = "/about", methods = {"GET"})
public class About__Page implements CandiPage {

    @Autowired
    private CandiLayout baseLayout;

    @Override
    public ActionResult handleAction(String method) {
        return ActionResult.methodNotAllowed();
    }

    @Override
    public void render(HtmlOutput out) {
        baseLayout.render(out, (slotName, slotOut) -> {
            switch (slotName) {
                case "title" -> slotOut.append("About Us");
                case "content" -> {
                    slotOut.append("<h1>About Candi</h1>\n");
                    slotOut.append("<p>An HTML-first framework for Spring Boot.</p>");
                }
                default -> {}
            }
        });
    }
}
