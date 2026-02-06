package candi.compiler.ast;

import candi.compiler.SourceLocation;

import java.util.List;

/**
 * Container for body content nodes (HTML + template expressions).
 */
public record BodyNode(List<Node> children, SourceLocation location) implements Node {
}
