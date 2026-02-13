package candi.runtime;

/**
 * Reusable POJO for rendering {@code <option>} elements in {@code {{ for }}} loops.
 *
 * <pre>
 * {{ for opt in options }}
 * {{ if opt.selected }}&lt;option value="{{ opt.value }}" selected&gt;{{ opt.label }}&lt;/option&gt;
 * {{ else }}&lt;option value="{{ opt.value }}"&gt;{{ opt.label }}&lt;/option&gt;{{ end }}
 * {{ end }}
 * </pre>
 */
public class SelectOption {

    private final String value;
    private final String label;
    private final Boolean selected;

    public SelectOption(String value, String label, Boolean selected) {
        this.value = value;
        this.label = label;
        this.selected = selected;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public Boolean getSelected() {
        return selected;
    }
}
