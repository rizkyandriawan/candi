package candi.demo.components;

import candi.runtime.CandiComponent;
import candi.runtime.HtmlOutput;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Hand-compiled component simulating compiler output for alert.component.html:
 *
 * @param type
 * @param message
 *
 * <div class="alert alert-{{ type }}">{{ message }}</div>
 */
@Component("Alert__Component")
@Scope("prototype")
public class Alert__Component implements CandiComponent {

    private String type = "info";
    private String message = "";

    @Override
    public void setParams(Map<String, Object> params) {
        if (params.containsKey("type")) {
            this.type = String.valueOf(params.get("type"));
        }
        if (params.containsKey("message")) {
            this.message = String.valueOf(params.get("message"));
        }
    }

    @Override
    public void render(HtmlOutput out) {
        out.append("<div class=\"alert alert-");
        out.appendEscaped(type);
        out.append("\">");
        out.appendEscaped(message);
        out.append("</div>");
    }
}
