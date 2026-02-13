package candi.compiler.ast;

import candi.compiler.SourceLocation;

/**
 * {{ block "name" }}content{{ end }}
 *
 * Used in pages to provide content for a named slot in the layout.
 */
public record BlockNode(
        String name,
        BodyNode body,
        SourceLocation location
) implements Node {
}
