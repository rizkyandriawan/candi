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
 * expression       = ternary
 * ternary          = null_coalesce ( "?" ternary ":" ternary )?
 * null_coalesce    = or_expr ( "??" null_coalesce )?
 * or_expr          = and_expr ( "||" and_expr )*
 * and_expr         = equality ( "&&" equality )*
 * equality         = comparison ( ("==" | "!=") comparison )*
 * comparison       = additive ( ("<" | ">" | "<=" | ">=") additive )*
 * additive         = multiplicative ( ("+" | "-") multiplicative )*
 * multiplicative   = string_concat ( ("*" | "/" | "%") string_concat )*
 * string_concat    = unary ( "~" unary )*
 * unary            = ("!" | "-") unary | filter_chain
 * filter_chain     = primary_chain ( "|" IDENTIFIER ( "(" args ")" )? )*
 * primary_chain    = primary ( ("." | "?.") member )* ( "[" expression "]" )*
 * primary          = IDENTIFIER | STRING | NUMBER | BOOLEAN | "(" expression ")"
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
        return parseTernary();
    }

    private Expression parseTernary() {
        Expression expr = parseNullCoalesce();
        if (check(TokenType.QUESTION)) {
            consume(); // ?
            Expression thenExpr = parseTernary();
            expect(TokenType.COLON, "':'");
            Expression elseExpr = parseTernary();
            return new Expression.Ternary(expr, thenExpr, elseExpr, expr.location());
        }
        return expr;
    }

    private Expression parseNullCoalesce() {
        Expression left = parseOrExpr();
        if (check(TokenType.NULL_COALESCE)) {
            consume(); // ??
            Expression right = parseNullCoalesce(); // right-associative
            return new Expression.NullCoalesce(left, right, left.location());
        }
        return left;
    }

    private Expression parseOrExpr() {
        Expression left = parseAndExpr();
        while (check(TokenType.OR)) {
            consume();
            Expression right = parseAndExpr();
            left = new Expression.BinaryOp(left, "||", right, left.location());
        }
        return left;
    }

    private Expression parseAndExpr() {
        Expression left = parseEquality();
        while (check(TokenType.AND)) {
            consume();
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
        Expression left = parseAdditive();
        while (check(TokenType.LT) || check(TokenType.GT) || check(TokenType.LTE) || check(TokenType.GTE)) {
            Token op = consume();
            Expression right = parseAdditive();
            left = new Expression.BinaryOp(left, op.value(), right, left.location());
        }
        return left;
    }

    private Expression parseAdditive() {
        Expression left = parseMultiplicative();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            Token op = consume();
            Expression right = parseMultiplicative();
            left = new Expression.BinaryOp(left, op.value(), right, left.location());
        }
        return left;
    }

    private Expression parseMultiplicative() {
        Expression left = parseStringConcat();
        while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.PERCENT)) {
            Token op = consume();
            Expression right = parseStringConcat();
            left = new Expression.BinaryOp(left, op.value(), right, left.location());
        }
        return left;
    }

    private Expression parseStringConcat() {
        Expression left = parseUnary();
        while (check(TokenType.TILDE)) {
            consume();
            Expression right = parseUnary();
            left = new Expression.BinaryOp(left, "~", right, left.location());
        }
        return left;
    }

    private Expression parseUnary() {
        if (check(TokenType.NOT)) {
            Token op = consume();
            Expression operand = parseUnary();
            return new Expression.UnaryNot(operand, op.location());
        }
        if (check(TokenType.MINUS)) {
            Token op = consume();
            Expression operand = parseUnary();
            return new Expression.UnaryMinus(operand, op.location());
        }
        return parseFilterChain();
    }

    private Expression parseFilterChain() {
        Expression expr = parsePrimaryChain();
        while (check(TokenType.PIPE)) {
            consume(); // |
            Token filterName = expect(TokenType.IDENTIFIER, "filter name");
            List<Expression> args = new ArrayList<>();
            if (check(TokenType.LPAREN)) {
                consume(); // (
                args = parseArguments();
                expect(TokenType.RPAREN, "')'");
            }
            expr = new Expression.FilterCall(expr, filterName.value(), args, expr.location());
        }
        return expr;
    }

    private Expression parsePrimaryChain() {
        Expression expr = parsePrimary();

        // Property/method chain and index access (interleaved)
        outer:
        while (true) {
            if (check(TokenType.DOT) || check(TokenType.NULL_SAFE_DOT)) {
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
            } else if (check(TokenType.LBRACKET)) {
                consume(); // [
                Expression index = parse(); // full expression for index
                expect(TokenType.RBRACKET, "']'");
                expr = new Expression.IndexAccess(expr, index, expr.location());
            } else {
                break outer;
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
