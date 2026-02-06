package candi.compiler.ast;

import candi.compiler.SourceLocation;

public record LayoutDirectiveNode(String layoutName, SourceLocation location) implements Node {
}
