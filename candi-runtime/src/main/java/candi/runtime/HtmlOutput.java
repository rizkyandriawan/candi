package candi.runtime;

/**
 * HTML output buffer used by generated page render methods.
 * Provides both raw append (for static HTML) and escaped append (for expressions).
 */
public class HtmlOutput {

    private final StringBuilder sb;
    private java.util.Map<String, java.util.List<String>> stacks;

    public HtmlOutput() {
        this.sb = new StringBuilder(4096);
    }

    public HtmlOutput(int initialCapacity) {
        this.sb = new StringBuilder(initialCapacity);
    }

    /**
     * Append raw HTML content (no escaping).
     * Used for static HTML fragments and {{ raw expr }} output.
     */
    public HtmlOutput append(String html) {
        sb.append(html);
        return this;
    }

    /**
     * Append HTML-escaped content.
     * Used for {{ expr }} output — prevents XSS.
     */
    public HtmlOutput appendEscaped(String text) {
        if (text == null) {
            return this;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#x27;");
                default -> sb.append(c);
            }
        }
        return this;
    }

    /**
     * Get the accumulated HTML string.
     */
    public String toHtml() {
        return sb.toString();
    }

    /**
     * Get the current length of the buffer.
     */
    public int length() {
        return sb.length();
    }

    /**
     * Push content to a named stack. Used by {{ push "name" }}...{{ end }}.
     */
    public void pushStack(String name, String content) {
        if (stacks == null) {
            stacks = new java.util.LinkedHashMap<>();
        }
        stacks.computeIfAbsent(name, k -> new java.util.ArrayList<>()).add(content);
    }

    /**
     * Render all content pushed to a named stack. Used by {{ stack "name" }}.
     */
    public void renderStack(String name) {
        if (stacks == null) return;
        var items = stacks.get(name);
        if (items != null) {
            for (var item : items) {
                sb.append(item);
            }
        }
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
