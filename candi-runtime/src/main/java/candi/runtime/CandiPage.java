package candi.runtime;

/**
 * Core interface implemented by all generated page classes.
 * Each .jhtml page compiles to a class implementing this interface.
 *
 * Lifecycle:
 *   1. init() — always runs (shared setup)
 *   2. For GET/HEAD: onGet() → render()
 *   3. For POST: onPost() → @Post action → redirect or render()
 *   4. For PUT/DELETE/PATCH: @Action → redirect or render()
 */
public interface CandiPage {

    /**
     * Shared setup. Called once per request before anything else.
     * Use for lightweight initialization (setting references, etc.).
     */
    default void init() {}

    /**
     * Called for GET/HEAD requests before rendering.
     * Put data loading here — this is skipped when POST/DELETE/etc redirects.
     */
    default void onGet() {}

    /**
     * Called for POST requests before @Post action dispatch.
     * Use for shared POST setup (CSRF validation, common form parsing, etc.).
     */
    default void onPost() {}

    /**
     * Render the full page HTML.
     */
    void render(HtmlOutput out);
}
