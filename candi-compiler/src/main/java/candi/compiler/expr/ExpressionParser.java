package candi.compiler.expr;

import candi.compiler.CompileError;
import candi.compiler.SourceLocation;
import candi.compiler.lexer.Token;
import candi.compiler.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive descent expression parser.
 * <p>
 * Grammar:
 * expression    = or_expr
 * or_expr       = and_expr ( "||" and_expr )*
 * and_expr      = equality ( "&&" equality )*
 * equality      = comparison ( ("==" | "!=") comparison )*
 * comparison    = unary ( ("<" | ">" | "<=" | ">=") unary )*
 * unary         = "!" unary | primary_chain
 * primary_chain = primary ( ("." | "?.") member )*
 * member        = IDENTIFIER ( "(" args? ")" )?
 * primary       = IDENTIFIER | STRING | NUMBER | BOOLEAN | "(" expression ")"
 */
public class ExpressionParser {

    private final List<Token> tokens;
    private int pos;

    public ExpressionParser(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    public int getPos() {
        return pos;
    }

    public Expression parse() {
        Expression expr = parseOrExpr();
        return expr;
    }

    private Expression parseOrExpr() {
        Expression left = parseAndExpr();
        while (check(TokenType.OR)) {
            Token op = consume();
            Expression right = parseAndExpr();
            left = new Expression.BinaryOp(left, "||", right, left.location());
        }
        return left;
    }

    private Expression parseAndExpr() {
        Expression left = parseEquality();
        while (check(TokenType.AND)) {
            Token op = consume();
            Expression right = parseEquality();
            left = new Expression.BinaryOp(left, "&&", right, left.location());
        }
        return left;
    }

    private Expression parseEquality() {
        Expression left = parseComparison();
        while (check(TokenType.EQ) || check(TokenType.NEQ)) {
            Token op = consume();
            Expression right = parseComparison();
            left = new Expression.BinaryOp(left, op.value(), right, left.location());
        }
        return left;
    }

    private Expression parseComparison() {
        Expression left = parseUnary();
        while (check(TokenType.LT) || check(TokenType.GT) || check(TokenType.LTE) || check(TokenType.GTE)) {
            Token op = consume();
            Expression right = parseUnary();
            left = new Expression.BinaryOp(left, op.value(), right, left.location());
        }
        return left;
    }

    private Expression parseUnary() {
        if (check(TokenType.NOT)) {
            Token op = consume();
            Expression operand = parseUnary();
            return new Expression.UnaryNot(operand, op.location());
        }
        return parsePrimaryChain();
    }

    private Expression parsePrimaryChain() {
        Expression expr = parsePrimary();

        while (check(TokenType.DOT) || check(TokenType.NULL_SAFE_DOT)) {
            boolean nullSafe = check(TokenType.NULL_SAFE_DOT);
            consume(); // . or ?.

            Token member = expect(TokenType.IDENTIFIER, "property or method name");
            String name = member.value();

            // Check for method call: name(args)
            if (check(TokenType.LPAREN)) {
                consume(); // (
                List<Expression> args = parseArguments();
                expect(TokenType.RPAREN, "')'");
                if (nullSafe) {
                    expr = new Expression.NullSafeMethodCall(expr, name, args, expr.location());
                } else {
                    expr = new Expression.MethodCall(expr, name, args, expr.location());
                }
            } else {
                // Property access
                if (nullSafe) {
                    expr = new Expression.NullSafePropertyAccess(expr, name, expr.location());
                } else {
                    expr = new Expression.PropertyAccess(expr, name, expr.location());
                }
            }
        }
        return expr;
    }

    private List<Expression> parseArguments() {
        List<Expression> args = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            args.add(parse());
            while (check(TokenType.COMMA)) {
                consume();
                args.add(parse());
            }
        }
        return args;
    }

    private Expression parsePrimary() {
        Token token = peek();
        if (token == null) {
            throw new CompileError("Unexpected end of expression",
                    tokens.isEmpty() ? new SourceLocation("unknown", 0, 0) : tokens.getLast().location());
        }

        return switch (token.type()) {
            case IDENTIFIER -> {
                consume();
                yield new Expression.Variable(token.value(), token.location());
            }
            case STRING_LITERAL -> {
                consume();
                yield new Expression.StringLiteral(token.value(), token.location());
            }
            case NUMBER -> {
                consume();
                yield new Expression.NumberLiteral(token.value(), token.location());
            }
            case TRUE -> {
                consume();
                yield new Expression.BooleanLiteral(true, token.location());
            }
            case FALSE -> {
                consume();
                yield new Expression.BooleanLiteral(false, token.location());
            }
            case LPAREN -> {
                consume(); // (
                Expression inner = parse();
                expect(TokenType.RPAREN, "')'");
                yield new Expression.Grouped(inner, token.location());
            }
            default -> throw new CompileError(
                    "Unexpected token " + token.type() + " in expression",
                    token.location());
        };
    }

    private boolean check(TokenType type) {
        Token t = peek();
        return t != null && t.type() == type;
    }

    private Token peek() {
        if (pos >= tokens.size()) return null;
        return tokens.get(pos);
    }

    private Token consume() {
        Token t = tokens.get(pos);
        pos++;
        return t;
    }

    private Token expect(TokenType type, String description) {
        Token t = peek();
        if (t == null || t.type() != type) {
            SourceLocation loc = t != null ? t.location()
                    : (!tokens.isEmpty() ? tokens.getLast().location() : new SourceLocation("unknown", 0, 0));
            throw new CompileError("Expected " + description + " but got " +
                    (t != null ? t.type() : "end of input"), loc);
        }
        return consume();
    }
}
