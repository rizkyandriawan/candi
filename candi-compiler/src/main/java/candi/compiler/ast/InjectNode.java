package candi.compiler.ast;

import candi.compiler.SourceLocation;

public record InjectNode(String typeName, String variableName, SourceLocation location) implements Node {
}
