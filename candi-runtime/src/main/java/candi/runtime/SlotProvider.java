package candi.runtime;

/**
 * Provides named slot content during layout rendering.
 * Slots are lambda callbacks — no intermediate buffering.
 */
@FunctionalInterface
public interface SlotProvider {

    /**
     * Render a named slot's content into the output buffer.
     * If the slot doesn't exist, this should be a no-op.
     *
     * @param slotName the slot name
     * @param out      the output buffer to write to
     */
    void renderSlot(String slotName, HtmlOutput out);
}
