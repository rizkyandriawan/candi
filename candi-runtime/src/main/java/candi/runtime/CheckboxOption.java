package candi.runtime;

/**
 * Reusable POJO for rendering checkbox/radio elements in {@code {{ for }}} loops.
 *
 * <pre>
 * {{ for role in roleCheckboxes }}
 * {{ if role.checked }}&lt;label&gt;&lt;input type="checkbox" name="roleIds" value="{{ role.value }}" checked&gt; {{ role.label }}&lt;/label&gt;
 * {{ else }}&lt;label&gt;&lt;input type="checkbox" name="roleIds" value="{{ role.value }}"&gt; {{ role.label }}&lt;/label&gt;{{ end }}
 * {{ end }}
 * </pre>
 */
public class CheckboxOption {

    private final String value;
    private final String label;
    private final Boolean checked;

    public CheckboxOption(String value, String label, Boolean checked) {
        this.value = value;
        this.label = label;
        this.checked = checked;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public Boolean getChecked() {
        return checked;
    }
}
