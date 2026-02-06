package candi.runtime;

import java.util.Map;

/**
 * Interface implemented by compiled component files (.component.html).
 * Components are reusable UI elements with parameters.
 */
public interface CandiComponent {

    /**
     * Set parameters for this component invocation.
     *
     * @param params map of parameter name → value
     */
    void setParams(Map<String, Object> params);

    /**
     * Render the component's HTML.
     *
     * @param out the output buffer to write to
     */
    void render(HtmlOutput out);
}
