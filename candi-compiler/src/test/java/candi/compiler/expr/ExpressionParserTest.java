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

    // ========== Ternary ==========

    @Test
    void testTernaryExpression() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "active"),
                tok(TokenType.QUESTION, "?"),
                tok(TokenType.STRING_LITERAL, "yes"),
                tok(TokenType.COLON, ":"),
                tok(TokenType.STRING_LITERAL, "no")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.Ternary.class, expr);
        var ternary = (Expression.Ternary) expr;
        assertInstanceOf(Expression.Variable.class, ternary.condition());
        assertInstanceOf(Expression.StringLiteral.class, ternary.thenExpr());
        assertInstanceOf(Expression.StringLiteral.class, ternary.elseExpr());
    }

    @Test
    void testTernaryWithComparison() {
        // status == "active" ? "yes" : "no"
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "status"),
                tok(TokenType.EQ, "=="),
                tok(TokenType.STRING_LITERAL, "active"),
                tok(TokenType.QUESTION, "?"),
                tok(TokenType.STRING_LITERAL, "yes"),
                tok(TokenType.COLON, ":"),
                tok(TokenType.STRING_LITERAL, "no")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.Ternary.class, expr);
        var ternary = (Expression.Ternary) expr;
        assertInstanceOf(Expression.BinaryOp.class, ternary.condition());
    }

    // ========== Null Coalescing ==========

    @Test
    void testNullCoalescing() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "name"),
                tok(TokenType.NULL_COALESCE, "??"),
                tok(TokenType.STRING_LITERAL, "Guest")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.NullCoalesce.class, expr);
        var nc = (Expression.NullCoalesce) expr;
        assertInstanceOf(Expression.Variable.class, nc.left());
        assertInstanceOf(Expression.StringLiteral.class, nc.fallback());
    }

    // ========== Arithmetic ==========

    @Test
    void testAddition() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "a"),
                tok(TokenType.PLUS, "+"),
                tok(TokenType.IDENTIFIER, "b")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.BinaryOp.class, expr);
        assertEquals("+", ((Expression.BinaryOp) expr).operator());
    }

    @Test
    void testSubtraction() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "a"),
                tok(TokenType.MINUS, "-"),
                tok(TokenType.IDENTIFIER, "b")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.BinaryOp.class, expr);
        assertEquals("-", ((Expression.BinaryOp) expr).operator());
    }

    @Test
    void testMultiplication() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "a"),
                tok(TokenType.STAR, "*"),
                tok(TokenType.IDENTIFIER, "b")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.BinaryOp.class, expr);
        assertEquals("*", ((Expression.BinaryOp) expr).operator());
    }

    @Test
    void testDivision() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "a"),
                tok(TokenType.SLASH, "/"),
                tok(TokenType.IDENTIFIER, "b")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.BinaryOp.class, expr);
        assertEquals("/", ((Expression.BinaryOp) expr).operator());
    }

    @Test
    void testModulus() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "a"),
                tok(TokenType.PERCENT, "%"),
                tok(TokenType.IDENTIFIER, "b")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.BinaryOp.class, expr);
        assertEquals("%", ((Expression.BinaryOp) expr).operator());
    }

    @Test
    void testPrecedence_multiplicationBeforeAddition() {
        // a + b * c  should parse as a + (b * c)
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "a"),
                tok(TokenType.PLUS, "+"),
                tok(TokenType.IDENTIFIER, "b"),
                tok(TokenType.STAR, "*"),
                tok(TokenType.IDENTIFIER, "c")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.BinaryOp.class, expr);
        var add = (Expression.BinaryOp) expr;
        assertEquals("+", add.operator());
        assertInstanceOf(Expression.BinaryOp.class, add.right());
        assertEquals("*", ((Expression.BinaryOp) add.right()).operator());
    }

    // ========== String Concatenation ==========

    @Test
    void testStringConcat() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "first"),
                tok(TokenType.TILDE, "~"),
                tok(TokenType.STRING_LITERAL, " "),
                tok(TokenType.TILDE, "~"),
                tok(TokenType.IDENTIFIER, "last")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.BinaryOp.class, expr);
        var concat = (Expression.BinaryOp) expr;
        assertEquals("~", concat.operator());
    }

    // ========== Unary Minus ==========

    @Test
    void testUnaryMinus() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.MINUS, "-"),
                tok(TokenType.IDENTIFIER, "x")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.UnaryMinus.class, expr);
        assertInstanceOf(Expression.Variable.class, ((Expression.UnaryMinus) expr).operand());
    }

    @Test
    void testUnaryMinusWithNumber() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.MINUS, "-"),
                tok(TokenType.NUMBER, "5")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.UnaryMinus.class, expr);
        assertInstanceOf(Expression.NumberLiteral.class, ((Expression.UnaryMinus) expr).operand());
    }

    // ========== Filters ==========

    @Test
    void testSimpleFilter() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "name"),
                tok(TokenType.PIPE, "|"),
                tok(TokenType.IDENTIFIER, "upper")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.FilterCall.class, expr);
        var filter = (Expression.FilterCall) expr;
        assertEquals("upper", filter.filterName());
        assertInstanceOf(Expression.Variable.class, filter.input());
        assertTrue(filter.arguments().isEmpty());
    }

    @Test
    void testFilterWithArguments() {
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "text"),
                tok(TokenType.PIPE, "|"),
                tok(TokenType.IDENTIFIER, "truncate"),
                tok(TokenType.LPAREN, "("),
                tok(TokenType.NUMBER, "100"),
                tok(TokenType.RPAREN, ")")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.FilterCall.class, expr);
        var filter = (Expression.FilterCall) expr;
        assertEquals("truncate", filter.filterName());
        assertEquals(1, filter.arguments().size());
    }

    @Test
    void testChainedFilters() {
        // name | trim | upper
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "name"),
                tok(TokenType.PIPE, "|"),
                tok(TokenType.IDENTIFIER, "trim"),
                tok(TokenType.PIPE, "|"),
                tok(TokenType.IDENTIFIER, "upper")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.FilterCall.class, expr);
        var outer = (Expression.FilterCall) expr;
        assertEquals("upper", outer.filterName());
        assertInstanceOf(Expression.FilterCall.class, outer.input());
        assertEquals("trim", ((Expression.FilterCall) outer.input()).filterName());
    }

    // ========== Index Access ==========

    @Test
    void testIndexAccessWithNumber() {
        // items[0]
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "items"),
                tok(TokenType.LBRACKET, "["),
                tok(TokenType.NUMBER, "0"),
                tok(TokenType.RBRACKET, "]")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.IndexAccess.class, expr);
        var ia = (Expression.IndexAccess) expr;
        assertInstanceOf(Expression.Variable.class, ia.object());
        assertInstanceOf(Expression.NumberLiteral.class, ia.index());
    }

    @Test
    void testIndexAccessWithString() {
        // map["key"]
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "map"),
                tok(TokenType.LBRACKET, "["),
                tok(TokenType.STRING_LITERAL, "key"),
                tok(TokenType.RBRACKET, "]")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.IndexAccess.class, expr);
        var ia = (Expression.IndexAccess) expr;
        assertInstanceOf(Expression.StringLiteral.class, ia.index());
    }

    @Test
    void testChainedIndexAndPropertyAccess() {
        // items[0].name
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "items"),
                tok(TokenType.LBRACKET, "["),
                tok(TokenType.NUMBER, "0"),
                tok(TokenType.RBRACKET, "]"),
                tok(TokenType.DOT, "."),
                tok(TokenType.IDENTIFIER, "name")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.PropertyAccess.class, expr);
        var prop = (Expression.PropertyAccess) expr;
        assertEquals("name", prop.property());
        assertInstanceOf(Expression.IndexAccess.class, prop.object());
    }

    // ========== Precedence: Ternary vs Null Coalescing ==========

    @Test
    void testTernaryBindsLooserThanNullCoalesce() {
        // a ?? b ? "yes" : "no" should parse as (a ?? b) ? "yes" : "no"
        var parser = new ExpressionParser(tokens(
                tok(TokenType.IDENTIFIER, "a"),
                tok(TokenType.NULL_COALESCE, "??"),
                tok(TokenType.IDENTIFIER, "b"),
                tok(TokenType.QUESTION, "?"),
                tok(TokenType.STRING_LITERAL, "yes"),
                tok(TokenType.COLON, ":"),
                tok(TokenType.STRING_LITERAL, "no")
        ));
        Expression expr = parser.parse();
        assertInstanceOf(Expression.Ternary.class, expr);
        var ternary = (Expression.Ternary) expr;
        assertInstanceOf(Expression.NullCoalesce.class, ternary.condition());
    }
}
