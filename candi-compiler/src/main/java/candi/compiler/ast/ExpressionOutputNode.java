package candi.compiler.ast;

import candi.compiler.SourceLocation;
import candi.compiler.expr.Expression;

/**
 * {{ expr }} — auto-escaped output.
 */
public record ExpressionOutputNode(Expression expression, SourceLocation location) implements Node {
}
