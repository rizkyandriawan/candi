package candi.runtime;

/**
 * Core interface implemented by all generated page classes.
 * Each .page.html compiles to a class implementing this interface.
 */
public interface CandiPage {

    /**
     * Initialize page state. Called once per request before rendering.
     * This is where @init block code runs — fetching data, computing variables.
     */
    default void init() {}

    /**
     * Handle a non-GET action (POST, PUT, DELETE, PATCH).
     * This is where @action block code runs.
     *
     * @param method the HTTP method (e.g. "POST", "DELETE")
     * @return action result — redirect, render, or error
     */
    default ActionResult handleAction(String method) {
        return ActionResult.methodNotAllowed();
    }

    /**
     * Render the full page HTML.
     * This is the compiled body of the .page.html file.
     */
    void render(HtmlOutput out);

    /**
     * Render a named fragment.
     * Used for HTMX partial updates — only the fragment HTML is returned.
     *
     * @param name the fragment name (from @fragment "name")
     * @param out  the output to write to
     * @throws FragmentNotFoundException if the fragment doesn't exist
     */
    default void renderFragment(String name, HtmlOutput out) {
        throw new FragmentNotFoundException(name);
    }
}
