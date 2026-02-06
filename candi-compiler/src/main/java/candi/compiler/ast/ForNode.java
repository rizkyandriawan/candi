package candi.compiler.ast;

import candi.compiler.SourceLocation;
import candi.compiler.expr.Expression;

/**
 * {{ for item in collection }} ... {{ end }}
 */
public record ForNode(
        String variableName,
        Expression collection,
        BodyNode body,
        SourceLocation location
) implements Node {
}
