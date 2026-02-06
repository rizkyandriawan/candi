package candi.compiler.ast;

import candi.compiler.SourceLocation;

/**
 * {{ content }} — placeholder in layout templates where page content is injected.
 */
public record ContentNode(SourceLocation location) implements Node {
}
