package candi.demo.pages;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import candi.runtime.*;

/**
 * Hand-compiled page simulating v2 compiler output for:
 *
 * @Page("/about")
 * @Layout("base")
 * public class AboutPage {
 * }
 *
 * <template>
 * <h1>About Candi</h1>
 * <p>An HTML-first framework for Spring Boot.</p>
 * </template>
 */
@Component
@Scope(WebApplicationContext.SCOPE_REQUEST)
@CandiRoute(path = "/about", methods = {"GET"})
public class About__Page implements CandiPage {

    @Autowired
    private CandiLayout baseLayout;

    @Override
    public void render(HtmlOutput out) {
        baseLayout.render(out, (slotName, slotOut) -> {
            if ("content".equals(slotName)) {
                slotOut.append("<h1>About Candi</h1>\n");
                slotOut.append("<p>An HTML-first framework for Spring Boot.</p>");
            }
        });
    }
}
