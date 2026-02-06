package candi.compiler.expr;

import candi.compiler.SourceLocation;
import candi.compiler.lexer.Token;
import candi.compiler.lexer.TokenType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionParserTest {

    private final SourceLocation loc = new SourceLocation("test", 1, 1);

    private List<Token> tokens(Token... tokens) {
        return List.of(tokens);
    }

    private Token tok(TokenType type, String value) {
        return new Token(type, value, loc);
    }

    @Test
    void testSimpleVariable() {
        var parser = new ExpressionParser(tokens(tok(TokenType.IDENTIFIER, "name")));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.Variable.class, expr);
        assertEquals("name", ((Expression.Variable) expr).name());
    }

    @Test
    void testStringLiteral() {
        var parser = new ExpressionParser(tokens(tok(TokenType.STRING_LITERAL, "hello")));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.StringLiteral.class, expr);
        assertEquals("hello", ((Expression.StringLiteral) expr).value());
    }

    @Test
    void testPropertyAccess() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "post"),
                tok(TokenType.DOT, "."),
                tok(TokenType.IDENTIFIER, "title")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.PropertyAccess.class, expr);
        var prop = (Expression.PropertyAccess) expr;
        assertEquals("title", prop.property());
        assertInstanceOf(Expression.Variable.class, prop.object());
    }

    @Test
    void testChainedPropertyAccess() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "post"),
                tok(TokenType.DOT, "."),
                tok(TokenType.IDENTIFIER, "author"),
                tok(TokenType.DOT, "."),
                tok(TokenType.IDENTIFIER, "name")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.PropertyAccess.class, expr);
        var name = (Expression.PropertyAccess) expr;
        assertEquals("name", name.property());
        assertInstanceOf(Expression.PropertyAccess.class, name.object());
    }

    @Test
    void testNullSafePropertyAccess() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "post"),
                tok(TokenType.NULL_SAFE_DOT, "?."),
                tok(TokenType.IDENTIFIER, "title")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.NullSafePropertyAccess.class, expr);
    }

    @Test
    void testMethodCall() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "post"),
                tok(TokenType.DOT, "."),
                tok(TokenType.IDENTIFIER, "getTitle"),
                tok(TokenType.LPAREN, "("),
                tok(TokenType.RPAREN, ")")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.MethodCall.class, expr);
        var call = (Expression.MethodCall) expr;
        assertEquals("getTitle", call.methodName());
        assertTrue(call.arguments().isEmpty());
    }

    @Test
    void testMethodCallWithArgs() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "list"),
                tok(TokenType.DOT, "."),
                tok(TokenType.IDENTIFIER, "get"),
                tok(TokenType.LPAREN, "("),
                tok(TokenType.NUMBER, "0"),
                tok(TokenType.RPAREN, ")")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.MethodCall.class, expr);
        var call = (Expression.MethodCall) expr;
        assertEquals(1, call.arguments().size());
    }

    @Test
    void testEquality() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "status"),
                tok(TokenType.EQ, "=="),
                tok(TokenType.STRING_LITERAL, "active")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.BinaryOp.class, expr);
        var binop = (Expression.BinaryOp) expr;
        assertEquals("==", binop.operator());
    }

    @Test
    void testBooleanAnd() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "a"),
                tok(TokenType.AND, "&&"),
                tok(TokenType.IDENTIFIER, "b")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.BinaryOp.class, expr);
        assertEquals("&&", ((Expression.BinaryOp) expr).operator());
    }

    @Test
    void testUnaryNot() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.NOT, "!"),
                tok(TokenType.IDENTIFIER, "visible")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.UnaryNot.class, expr);
    }

    @Test
    void testGroupedExpression() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.LPAREN, "("),
                tok(TokenType.IDENTIFIER, "a"),
                tok(TokenType.OR, "||"),
                tok(TokenType.IDENTIFIER, "b"),
                tok(TokenType.RPAREN, ")")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.Grouped.class, expr);
    }

    @Test
    void testBooleanLiterals() {
        var parser = new ExpressionParser(tokens(tok(TokenType.TRUE, "true")));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.BooleanLiteral.class, expr);
        assertTrue(((Expression.BooleanLiteral) expr).value());
    }

    @Test
    void testPrecedence_andBeforeOr() {
        // a || b && c  should parse as a || (b && c)
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "a"),
                tok(TokenType.OR, "||"),
                tok(TokenType.IDENTIFIER, "b"),
                tok(TokenType.AND, "&&"),
                tok(TokenType.IDENTIFIER, "c")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.BinaryOp.class, expr);
        var or = (Expression.BinaryOp) expr;
        assertEquals("||", or.operator());
        // Right side should be (b && c)
        assertInstanceOf(Expression.BinaryOp.class, or.right());
        assertEquals("&&", ((Expression.BinaryOp) or.right()).operator());
    }
}
