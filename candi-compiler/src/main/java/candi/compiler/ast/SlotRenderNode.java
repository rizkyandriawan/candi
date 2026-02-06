package candi.compiler.ast;

import candi.compiler.SourceLocation;

/**
 * {{ slot name }} — render a slot (used in layouts).
 */
public record SlotRenderNode(String slotName, SourceLocation location) implements Node {
}
