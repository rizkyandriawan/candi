package candi.compiler.ast;

import candi.compiler.SourceLocation;
import candi.compiler.expr.Expression;

/**
 * {{ if cond }} ... {{ else }} ... {{ end }}
 */
public record IfNode(
        Expression condition,
        BodyNode thenBody,
        BodyNode elseBody,   // null if no else branch
        SourceLocation location
) implements Node {
}
