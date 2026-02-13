package candi.compiler.ast;

import candi.compiler.SourceLocation;
import candi.compiler.expr.Expression;

/**
 * {{ set x = expr }}
 */
public record SetNode(
        String variableName,
        Expression value,
        SourceLocation location
) implements Node {
}
