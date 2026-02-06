package candi.compiler.expr;

import candi.compiler.SourceLocation;

public sealed interface Expression permits
        Expression.Variable,
        Expression.StringLiteral,
        Expression.NumberLiteral,
        Expression.BooleanLiteral,
        Expression.PropertyAccess,
        Expression.NullSafePropertyAccess,
        Expression.MethodCall,
        Expression.NullSafeMethodCall,
        Expression.BinaryOp,
        Expression.UnaryNot,
        Expression.Grouped {

    SourceLocation location();

    /** Variable reference: post, items, canEdit */
    record Variable(String name, SourceLocation location) implements Expression {}

    /** String literal: "active" */
    record StringLiteral(String value, SourceLocation location) implements Expression {}

    /** Numeric literal: 42, 3.14 */
    record NumberLiteral(String value, SourceLocation location) implements Expression {}

    /** true / false */
    record BooleanLiteral(boolean value, SourceLocation location) implements Expression {}

    /** Property access: expr.name → expr.getName() */
    record PropertyAccess(Expression object, String property, SourceLocation location) implements Expression {}

    /** Null-safe property access: expr?.name */
    record NullSafePropertyAccess(Expression object, String property, SourceLocation location) implements Expression {}

    /** Method call: expr.method(args) */
    record MethodCall(Expression object, String methodName, java.util.List<Expression> arguments, SourceLocation location) implements Expression {}

    /** Null-safe method call: expr?.method(args) */
    record NullSafeMethodCall(Expression object, String methodName, java.util.List<Expression> arguments, SourceLocation location) implements Expression {}

    /** Binary operation: ==, !=, <, >, <=, >=, &&, || */
    record BinaryOp(Expression left, String operator, Expression right, SourceLocation location) implements Expression {}

    /** Unary not: !expr */
    record UnaryNot(Expression operand, SourceLocation location) implements Expression {}

    /** Parenthesized expression: (expr) */
    record Grouped(Expression inner, SourceLocation location) implements Expression {}
}
