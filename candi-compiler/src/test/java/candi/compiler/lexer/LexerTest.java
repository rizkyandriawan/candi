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
}
