package candi.demo.pages;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import candi.runtime.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Hand-compiled page simulating v2 compiler output for:
 *
 * @Page("/dashboard")
 * @Layout("base")
 * public class DashboardPage {
 * }
 *
 * <template>
 * <h1>Dashboard</h1>
 * {{ component "alert" type="success" message="Welcome back!" }}
 * {{ component "alert" type="warning" message="3 items need review." }}
 * </template>
 */
@Component
@Scope(WebApplicationContext.SCOPE_REQUEST)
@CandiRoute(path = "/dashboard", methods = {"GET"})
public class Dashboard__Page implements CandiPage {

    @Autowired
    private CandiLayout baseLayout;

    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public void render(HtmlOutput out) {
        baseLayout.render(out, (slotName, slotOut) -> {
            if ("content".equals(slotName)) {
                slotOut.append("<h1>Dashboard</h1>\n");
                // Component call: {{ component "alert" type="success" message="Welcome back!" }}
                {
                    CandiComponent _comp = applicationContext.getBean("Alert__Component", CandiComponent.class);
                    Map<String, Object> _params = new HashMap<>();
                    _params.put("type", "success");
                    _params.put("message", "Welcome back!");
                    _comp.setParams(_params);
                    _comp.render(slotOut);
                }
                slotOut.append("\n");
                // Component call: {{ component "alert" type="warning" message="3 items need review." }}
                {
                    CandiComponent _comp = applicationContext.getBean("Alert__Component", CandiComponent.class);
                    Map<String, Object> _params = new HashMap<>();
                    _params.put("type", "warning");
                    _params.put("message", "3 items need review.");
                    _comp.setParams(_params);
                    _comp.render(slotOut);
                }
            }
        });
    }
}
