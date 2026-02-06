package candi.demo.layouts;

import candi.runtime.CandiLayout;
import candi.runtime.HtmlOutput;
import candi.runtime.SlotProvider;
import org.springframework.stereotype.Component;

/**
 * Hand-compiled layout simulating v2 compiler output for base.layout.html:
 *
 * <template>
 * <html>
 * <head><title>Candi Demo</title></head>
 * <body>
 *   <nav>Candi Demo</nav>
 *   {{ content }}
 *   <footer>Powered by Candi</footer>
 * </body>
 * </html>
 * </template>
 */
@Component("baseLayout")
public class Base__Layout implements CandiLayout {

    @Override
    public void render(HtmlOutput out, SlotProvider slots) {
        out.append("<html>\n<head><title>Candi Demo</title></head>\n<body>\n<nav>Candi Demo</nav>\n");
        slots.renderSlot("content", out);
        out.append("\n<footer>Powered by Candi</footer>\n</body>\n</html>");
    }
}
