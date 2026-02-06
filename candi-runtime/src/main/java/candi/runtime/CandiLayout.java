package candi.runtime;

/**
 * Interface implemented by compiled layout files (.layout.html).
 * Layouts define a page structure with named slots that pages fill.
 */
public interface CandiLayout {

    /**
     * Render the layout, invoking slot callbacks to fill content.
     *
     * @param out   the HTML output buffer
     * @param slots provider for resolving named slots
     */
    void render(HtmlOutput out, SlotProvider slots);
}
