package candi.compiler.ast;

import candi.compiler.SourceLocation;

/**
 * {{ stack "scripts" }}
 *
 * Used in layouts to render all content pushed to a named stack.
 */
public record StackNode(
        String name,
        SourceLocation location
) implements Node {
}
