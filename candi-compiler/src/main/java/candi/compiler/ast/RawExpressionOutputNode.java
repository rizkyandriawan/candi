package candi.compiler.ast;

import candi.compiler.SourceLocation;
import candi.compiler.expr.Expression;

/**
 * {{ raw expr }} — unescaped output.
 */
public record RawExpressionOutputNode(Expression expression, SourceLocation location) implements Node {
}
