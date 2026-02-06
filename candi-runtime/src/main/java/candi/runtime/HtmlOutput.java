package candi.runtime;

/**
 * HTML output buffer used by generated page render methods.
 * Provides both raw append (for static HTML) and escaped append (for expressions).
 */
public class HtmlOutput {

    private final StringBuilder sb;

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

    @Override
    public String toString() {
        return sb.toString();
    }
}
