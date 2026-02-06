package candi.runtime;

/**
 * Core interface implemented by all generated page classes.
 * Each .page.html compiles to a class implementing this interface.
 *
 * In v2, action dispatch is handled via reflection on @Post/@Delete/etc annotated methods.
 * Fragments are replaced by components and includes.
 */
public interface CandiPage {

    /**
     * Initialize page state. Called once per request before rendering.
     * This is where the init() method code runs — fetching data, computing variables.
     */
    default void init() {}

    /**
     * Render the full page HTML.
     * This is the compiled template of the .page.html file.
     */
    void render(HtmlOutput out);
}
