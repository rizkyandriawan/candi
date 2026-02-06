package candi.runtime;

/**
 * Result of an @action block execution.
 */
public sealed interface ActionResult {

    /**
     * Redirect to another URL (HTTP 302).
     */
    record Redirect(String url) implements ActionResult {}

    /**
     * Fall through to render the page (action succeeded, show the page).
     */
    record Render() implements ActionResult {}

    /**
     * HTTP 405 Method Not Allowed.
     */
    record MethodNotAllowed() implements ActionResult {}

    // Factory methods

    static ActionResult redirect(String url) {
        return new Redirect(url);
    }

    static ActionResult render() {
        return new Render();
    }

    static ActionResult methodNotAllowed() {
        return new MethodNotAllowed();
    }
}
