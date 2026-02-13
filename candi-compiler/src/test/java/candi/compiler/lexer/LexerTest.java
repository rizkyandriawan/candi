package candi.compiler.lexer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LexerTest {

    @Test
    void testBodyOnlyTemplate() {
        String source = "<h1>Hello</h1>";
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals("", lexer.getJavaSource());
        assertEquals(TokenType.HTML, tokens.get(0).type());
        assertTrue(tokens.get(0).value().contains("<h1>Hello</h1>"));
    }

    @Test
    void testJavaClassAndTemplate() {
        String source = """
                @Page("/hello")
                public class HelloPage {
                }

                <template>
                <h1>Hello</h1>
                </template>
                """;
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertTrue(lexer.getJavaSource().contains("@Page(\"/hello\")"));
        assertTrue(lexer.getJavaSource().contains("class HelloPage"));
        assertEquals(TokenType.HTML, tokens.get(0).type());
        assertTrue(tokens.get(0).value().contains("<h1>Hello</h1>"));
    }

    @Test
    void testExpressionInBody() {
        String source = """
                <template>
                <h1>{{ title }}</h1>
                </template>
                """;
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.HTML, tokens.get(0).type());
        assertEquals("<h1>", tokens.get(0).value());
        assertEquals(TokenType.EXPR_START, tokens.get(1).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).type());
        assertEquals("title", tokens.get(2).value());
        assertEquals(TokenType.EXPR_END, tokens.get(3).type());
        assertEquals(TokenType.HTML, tokens.get(4).type());
    }

    @Test
    void testIfExpression() {
        String source = "{{ if visible }}<p>yes</p>{{ end }}";
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.EXPR_START, tokens.get(0).type());
        assertEquals(TokenType.KEYWORD_IF, tokens.get(1).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).type());
        assertEquals("visible", tokens.get(2).value());
        assertEquals(TokenType.EXPR_END, tokens.get(3).type());
        assertEquals(TokenType.HTML, tokens.get(4).type());
        assertEquals(TokenType.EXPR_START, tokens.get(5).type());
        assertEquals(TokenType.KEYWORD_END, tokens.get(6).type());
        assertEquals(TokenType.EXPR_END, tokens.get(7).type());
    }

    @Test
    void testForExpression() {
        String source = "{{ for item in items }}<li>{{ item.name }}</li>{{ end }}";
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.EXPR_START, tokens.get(0).type());
        assertEquals(TokenType.KEYWORD_FOR, tokens.get(1).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).type());
        assertEquals("item", tokens.get(2).value());
        assertEquals(TokenType.KEYWORD_IN, tokens.get(3).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(4).type());
        assertEquals("items", tokens.get(4).value());
        assertEquals(TokenType.EXPR_END, tokens.get(5).type());
    }

    @Test
    void testPropertyAccessExpression() {
        String source = "{{ post.title }}";
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.EXPR_START, tokens.get(0).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(1).type());
        assertEquals("post", tokens.get(1).value());
        assertEquals(TokenType.DOT, tokens.get(2).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(3).type());
        assertEquals("title", tokens.get(3).value());
        assertEquals(TokenType.EXPR_END, tokens.get(4).type());
    }

    @Test
    void testNullSafeAccess() {
        String source = "{{ post?.title }}";
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.IDENTIFIER, tokens.get(1).type());
        assertEquals(TokenType.NULL_SAFE_DOT, tokens.get(2).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(3).type());
    }

    @Test
    void testBooleanOperators() {
        String source = "{{ if a && b || c }}yes{{ end }}";
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.KEYWORD_IF, tokens.get(1).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).type());
        assertEquals(TokenType.AND, tokens.get(3).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(4).type());
        assertEquals(TokenType.OR, tokens.get(5).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(6).type());
    }

    @Test
    void testComparisonOperators() {
        String source = "{{ if status == \"active\" }}yes{{ end }}";
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.KEYWORD_IF, tokens.get(1).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).type());
        assertEquals(TokenType.EQ, tokens.get(3).type());
        assertEquals(TokenType.STRING_LITERAL, tokens.get(4).type());
        assertEquals("active", tokens.get(4).value());
    }

    @Test
    void testRawExpression() {
        String source = "{{ raw post.htmlContent }}";
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.EXPR_START, tokens.get(0).type());
        assertEquals(TokenType.KEYWORD_RAW, tokens.get(1).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).type());
        assertEquals("post", tokens.get(2).value());
    }

    @Test
    void testIncludeExpression() {
        String source = "{{ include \"header\" title=\"Home\" }}";
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.EXPR_START, tokens.get(0).type());
        assertEquals(TokenType.KEYWORD_INCLUDE, tokens.get(1).type());
        assertEquals(TokenType.STRING_LITERAL, tokens.get(2).type());
        assertEquals("header", tokens.get(2).value());
        assertEquals(TokenType.IDENTIFIER, tokens.get(3).type());
        assertEquals("title", tokens.get(3).value());
        assertEquals(TokenType.EQUALS_SIGN, tokens.get(4).type());
        assertEquals(TokenType.STRING_LITERAL, tokens.get(5).type());
        assertEquals("Home", tokens.get(5).value());
    }

    @Test
    void testComponentExpression() {
        String source = "{{ component \"alert\" type=\"error\" message=\"Oops\" }}";
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.EXPR_START, tokens.get(0).type());
        assertEquals(TokenType.KEYWORD_COMPONENT, tokens.get(1).type());
        assertEquals(TokenType.STRING_LITERAL, tokens.get(2).type());
        assertEquals("alert", tokens.get(2).value());
        assertEquals(TokenType.IDENTIFIER, tokens.get(3).type());
        assertEquals("type", tokens.get(3).value());
    }

    @Test
    void testWidgetExpression() {
        String source = "{{ widget \"alert\" type=\"error\" message=\"Oops\" }}";
        Lexer lexer = new Lexer(source, "test.jhtml");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.EXPR_START, tokens.get(0).type());
        assertEquals(TokenType.KEYWORD_WIDGET, tokens.get(1).type());
        assertEquals(TokenType.STRING_LITERAL, tokens.get(2).type());
        assertEquals("alert", tokens.get(2).value());
        assertEquals(TokenType.IDENTIFIER, tokens.get(3).type());
        assertEquals("type", tokens.get(3).value());
    }

    @Test
    void testContentExpression() {
        String source = "{{ content }}";
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.EXPR_START, tokens.get(0).type());
        assertEquals(TokenType.KEYWORD_CONTENT, tokens.get(1).type());
        assertEquals(TokenType.EXPR_END, tokens.get(2).type());
    }

    @Test
    void testElseIfExpression() {
        String source = "{{ if a }}1{{ else if b }}2{{ else }}3{{ end }}";
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.EXPR_START, tokens.get(0).type());
        assertEquals(TokenType.KEYWORD_IF, tokens.get(1).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).type());
        assertEquals("a", tokens.get(2).value());
        assertEquals(TokenType.EXPR_END, tokens.get(3).type());
        assertEquals(TokenType.HTML, tokens.get(4).type());
        assertEquals(TokenType.EXPR_START, tokens.get(5).type());
        assertEquals(TokenType.KEYWORD_ELSE, tokens.get(6).type());
        assertEquals(TokenType.KEYWORD_IF, tokens.get(7).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(8).type());
        assertEquals("b", tokens.get(8).value());
        assertEquals(TokenType.EXPR_END, tokens.get(9).type());
    }

    @Test
    void testMethodCallExpression() {
        String source = "{{ post.getTitle() }}";
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.IDENTIFIER, tokens.get(1).type());
        assertEquals("post", tokens.get(1).value());
        assertEquals(TokenType.DOT, tokens.get(2).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(3).type());
        assertEquals("getTitle", tokens.get(3).value());
        assertEquals(TokenType.LPAREN, tokens.get(4).type());
        assertEquals(TokenType.RPAREN, tokens.get(5).type());
    }

    @Test
    void testJavaSourceExtraction() {
        String source = """
                @Page("/posts")
                public class PostsPage {

                    @Autowired
                    private PostService posts;

                    private List<Post> allPosts;

                    public void init() {
                        allPosts = posts.findAll();
                    }
                }

                <template>
                <h1>Posts</h1>
                </template>
                """;
        Lexer lexer = new Lexer(source, "test.page.html");
        lexer.tokenize();

        String javaSource = lexer.getJavaSource();
        assertTrue(javaSource.contains("@Page(\"/posts\")"));
        assertTrue(javaSource.contains("class PostsPage"));
        assertTrue(javaSource.contains("private PostService posts"));
        assertTrue(javaSource.contains("public void init()"));
    }

    @Test
    void testFragmentExpression() {
        String source = "{{ fragment \"post-list\" }}<ul>items</ul>{{ end }}";
        Lexer lexer = new Lexer(source, "test.jhtml");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.EXPR_START, tokens.get(0).type());
        assertEquals(TokenType.KEYWORD_FRAGMENT, tokens.get(1).type());
        assertEquals(TokenType.STRING_LITERAL, tokens.get(2).type());
        assertEquals("post-list", tokens.get(2).value());
        assertEquals(TokenType.EXPR_END, tokens.get(3).type());
        assertEquals(TokenType.HTML, tokens.get(4).type());
        assertEquals("<ul>items</ul>", tokens.get(4).value());
        assertEquals(TokenType.EXPR_START, tokens.get(5).type());
        assertEquals(TokenType.KEYWORD_END, tokens.get(6).type());
        assertEquals(TokenType.EXPR_END, tokens.get(7).type());
    }

    @Test
    void testIncludeSimple() {
        String source = "{{ include \"footer\" }}";
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.EXPR_START, tokens.get(0).type());
        assertEquals(TokenType.KEYWORD_INCLUDE, tokens.get(1).type());
        assertEquals(TokenType.STRING_LITERAL, tokens.get(2).type());
        assertEquals("footer", tokens.get(2).value());
        assertEquals(TokenType.EXPR_END, tokens.get(3).type());
    }

    // ========== Phase 1: Comments, Whitespace Control, Verbatim ==========

    @Test
    void testTemplateComment() {
        String source = "<h1>Hello</h1>{{-- this is a comment --}}<p>World</p>";
        List<Token> tokens = Lexer.tokenizeTemplate(source, "test.jhtml");

        // Comment should be completely stripped — only HTML tokens remain
        assertEquals(2, tokens.stream().filter(t -> t.type() == TokenType.HTML).count());
        String combined = tokens.stream()
                .filter(t -> t.type() == TokenType.HTML)
                .map(Token::value)
                .reduce("", String::concat);
        assertEquals("<h1>Hello</h1><p>World</p>", combined);
    }

    @Test
    void testCommentDoesNotEmitTokens() {
        String source = "{{-- only a comment --}}";
        List<Token> tokens = Lexer.tokenizeTemplate(source, "test.jhtml");

        // Should produce no tokens at all (or only EOF-like empty)
        assertTrue(tokens.stream().noneMatch(t -> t.type() == TokenType.EXPR_START));
    }

    @Test
    void testWhitespaceControlTrimLeft() {
        String source = "<p>hello</p>   \n  {{- title }}";
        List<Token> tokens = Lexer.tokenizeTemplate(source, "test.jhtml");

        // The HTML before {{- should have trailing whitespace trimmed
        Token html = tokens.get(0);
        assertEquals(TokenType.HTML, html.type());
        assertEquals("<p>hello</p>", html.value());
    }

    @Test
    void testWhitespaceControlTrimRight() {
        String source = "{{ title -}}   \n  <p>hello</p>";
        List<Token> tokens = Lexer.tokenizeTemplate(source, "test.jhtml");

        // The HTML after -}} should have leading whitespace trimmed
        Token html = tokens.stream()
                .filter(t -> t.type() == TokenType.HTML)
                .reduce((a, b) -> b).orElseThrow();
        assertEquals("<p>hello</p>", html.value());
    }

    @Test
    void testWhitespaceControlBothSides() {
        String source = "<p>A</p>  \n  {{- title -}}  \n  <p>B</p>";
        List<Token> tokens = Lexer.tokenizeTemplate(source, "test.jhtml");

        List<Token> htmlTokens = tokens.stream()
                .filter(t -> t.type() == TokenType.HTML).toList();
        assertEquals("<p>A</p>", htmlTokens.get(0).value());
        assertEquals("<p>B</p>", htmlTokens.get(1).value());
    }

    @Test
    void testVerbatimBlock() {
        String source = "{{ verbatim }}<p>{{ this is not parsed }}</p>{{ end }}";
        List<Token> tokens = Lexer.tokenizeTemplate(source, "test.jhtml");

        // Verbatim content should become a single HTML token with raw content
        Token html = tokens.stream()
                .filter(t -> t.type() == TokenType.HTML)
                .findFirst().orElseThrow();
        assertTrue(html.value().contains("{{ this is not parsed }}"));
    }

    // ========== Phase 2: New Operator Tokens ==========

    @Test
    void testTernaryTokens() {
        String source = "{{ cond ? \"yes\" : \"no\" }}";
        List<Token> tokens = Lexer.tokenizeTemplate(source, "test.jhtml");

        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.QUESTION));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.COLON));
    }

    @Test
    void testNullCoalesceTokens() {
        String source = "{{ name ?? \"Guest\" }}";
        List<Token> tokens = Lexer.tokenizeTemplate(source, "test.jhtml");

        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.NULL_COALESCE));
    }

    @Test
    void testArithmeticTokens() {
        String source = "{{ a + b * c - d / e % f }}";
        List<Token> tokens = Lexer.tokenizeTemplate(source, "test.jhtml");

        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.PLUS));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.STAR));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.MINUS));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.SLASH));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.PERCENT));
    }

    @Test
    void testTildeToken() {
        String source = "{{ first ~ \" \" ~ last }}";
        List<Token> tokens = Lexer.tokenizeTemplate(source, "test.jhtml");

        long tildeCount = tokens.stream().filter(t -> t.type() == TokenType.TILDE).count();
        assertEquals(2, tildeCount);
    }

    @Test
    void testPipeToken() {
        String source = "{{ name | upper }}";
        List<Token> tokens = Lexer.tokenizeTemplate(source, "test.jhtml");

        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.PIPE));
        // Make sure single | is PIPE, not OR
        assertFalse(tokens.stream().anyMatch(t -> t.type() == TokenType.OR));
    }

    @Test
    void testPipeVsOr() {
        String source = "{{ if a || b | upper }}yes{{ end }}";
        List<Token> tokens = Lexer.tokenizeTemplate(source, "test.jhtml");

        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.OR));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.PIPE));
    }

    @Test
    void testBracketTokens() {
        String source = "{{ items[0] }}";
        List<Token> tokens = Lexer.tokenizeTemplate(source, "test.jhtml");

        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.LBRACKET));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.RBRACKET));
    }

    // ========== Phase 5: New Keyword Tokens ==========

    @Test
    void testSetKeyword() {
        String source = "{{ set x = 42 }}";
        List<Token> tokens = Lexer.tokenizeTemplate(source, "test.jhtml");

        assertEquals(TokenType.KEYWORD_SET, tokens.get(1).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).type());
        assertEquals("x", tokens.get(2).value());
        assertEquals(TokenType.EQUALS_SIGN, tokens.get(3).type());
        assertEquals(TokenType.NUMBER, tokens.get(4).type());
    }

    @Test
    void testSwitchCaseKeywords() {
        String source = "{{ switch status }}{{ case \"active\" }}yes{{ default }}no{{ end }}";
        List<Token> tokens = Lexer.tokenizeTemplate(source, "test.jhtml");

        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.KEYWORD_SWITCH));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.KEYWORD_CASE));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.KEYWORD_DEFAULT));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == TokenType.KEYWORD_END));
    }

    @Test
    void testSlotKeyword() {
        String source = "{{ slot \"sidebar\" }}default{{ end }}";
        List<Token> tokens = Lexer.tokenizeTemplate(source, "test.jhtml");

        assertEquals(TokenType.KEYWORD_SLOT, tokens.get(1).type());
        assertEquals(TokenType.STRING_LITERAL, tokens.get(2).type());
        assertEquals("sidebar", tokens.get(2).value());
    }

    @Test
    void testBlockKeyword() {
        String source = "{{ block \"sidebar\" }}<nav>menu</nav>{{ end }}";
        List<Token> tokens = Lexer.tokenizeTemplate(source, "test.jhtml");

        assertEquals(TokenType.KEYWORD_BLOCK, tokens.get(1).type());
        assertEquals(TokenType.STRING_LITERAL, tokens.get(2).type());
        assertEquals("sidebar", tokens.get(2).value());
    }

    @Test
    void testStackKeyword() {
        String source = "{{ stack \"scripts\" }}";
        List<Token> tokens = Lexer.tokenizeTemplate(source, "test.jhtml");

        assertEquals(TokenType.KEYWORD_STACK, tokens.get(1).type());
        assertEquals(TokenType.STRING_LITERAL, tokens.get(2).type());
        assertEquals("scripts", tokens.get(2).value());
    }

    @Test
    void testPushKeyword() {
        String source = "{{ push \"scripts\" }}<script>alert(1)</script>{{ end }}";
        List<Token> tokens = Lexer.tokenizeTemplate(source, "test.jhtml");

        assertEquals(TokenType.KEYWORD_PUSH, tokens.get(1).type());
        assertEquals(TokenType.STRING_LITERAL, tokens.get(2).type());
        assertEquals("scripts", tokens.get(2).value());
    }
}
