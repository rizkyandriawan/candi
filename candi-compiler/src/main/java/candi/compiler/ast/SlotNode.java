package candi.compiler.ast;

import candi.compiler.SourceLocation;

/**
 * {{ slot "name" }}default content{{ end }}
 *
 * Used in layouts to declare a named slot with optional default content.
 */
public record SlotNode(
        String name,
        BodyNode defaultContent,  // nullable if self-closing
        SourceLocation location
) implements Node {
}
