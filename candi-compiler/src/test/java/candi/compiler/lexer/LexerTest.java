package candi.compiler.lexer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LexerTest {

    @Test
    void testSimplePageDirective() {
        String source = """
                @page "/hello"

                <h1>Hello</h1>
                """;
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.PAGE, tokens.get(0).type());
        assertEquals(TokenType.STRING_LITERAL, tokens.get(1).type());
        assertEquals("/hello", tokens.get(1).value());
        assertEquals(TokenType.HTML, tokens.get(2).type());
        assertTrue(tokens.get(2).value().contains("<h1>Hello</h1>"));
    }

    @Test
    void testInjectDirective() {
        String source = """
                @inject PostService posts

                <div></div>
                """;
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.INJECT, tokens.get(0).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(1).type());
        assertEquals("PostService", tokens.get(1).value());
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).type());
        assertEquals("posts", tokens.get(2).value());
    }

    @Test
    void testGenericTypeInject() {
        String source = """
                @inject List<Post> posts

                <div></div>
                """;
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.IDENTIFIER, tokens.get(1).type());
        assertEquals("List<Post>", tokens.get(1).value());
    }

    @Test
    void testInitBlock() {
        String source = """
                @init {
                  title = "Hello World";
                }

                <div></div>
                """;
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.INIT, tokens.get(0).type());
        assertEquals(TokenType.CODE_BLOCK, tokens.get(1).type());
        assertTrue(tokens.get(1).value().contains("title = \"Hello World\";"));
    }

    @Test
    void testActionBlock() {
        String source = """
                @action POST {
                  posts.save(ctx.form("title"));
                  redirect("/posts");
                }

                <div></div>
                """;
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.ACTION, tokens.get(0).type());
        assertEquals(TokenType.HTTP_METHOD, tokens.get(1).type());
        assertEquals("POST", tokens.get(1).value());
        assertEquals(TokenType.CODE_BLOCK, tokens.get(2).type());
    }

    @Test
    void testExpressionInBody() {
        String source = """
                <h1>{{ title }}</h1>
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
        String source = """
                {{ if visible }}<p>yes</p>{{ end }}
                """;
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
        String source = """
                {{ for item in items }}<li>{{ item.name }}</li>{{ end }}
                """;
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
        String source = """
                {{ post.title }}
                """;
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
        String source = """
                {{ post?.title }}
                """;
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.IDENTIFIER, tokens.get(1).type());
        assertEquals(TokenType.NULL_SAFE_DOT, tokens.get(2).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(3).type());
    }

    @Test
    void testBooleanOperators() {
        String source = """
                {{ if a && b || c }}yes{{ end }}
                """;
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
        String source = """
                {{ if status == "active" }}yes{{ end }}
                """;
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
        String source = """
                {{ raw post.htmlContent }}
                """;
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.EXPR_START, tokens.get(0).type());
        assertEquals(TokenType.KEYWORD_RAW, tokens.get(1).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).type());
        assertEquals("post", tokens.get(2).value());
    }

    @Test
    void testFragmentDirective() {
        String source = """
                @fragment "post-content" {
                  <article>{{ post.content }}</article>
                }

                <div></div>
                """;
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.FRAGMENT_DEF, tokens.get(0).type());
        assertEquals(TokenType.STRING_LITERAL, tokens.get(1).type());
        assertEquals("post-content", tokens.get(1).value());
        assertEquals(TokenType.CODE_BLOCK, tokens.get(2).type());
        assertTrue(tokens.get(2).value().contains("<article>"));
        assertTrue(tokens.get(2).value().contains("{{ post.content }}"));
    }

    @Test
    void testFragmentCall() {
        String source = """
                {{ fragment "post-content" }}
                """;
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.EXPR_START, tokens.get(0).type());
        assertEquals(TokenType.KEYWORD_FRAGMENT, tokens.get(1).type());
        assertEquals(TokenType.STRING_LITERAL, tokens.get(2).type());
        assertEquals("post-content", tokens.get(2).value());
    }

    @Test
    void testElseIfExpression() {
        String source = """
                {{ if a }}1{{ else if b }}2{{ else }}3{{ end }}
                """;
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        // {{ if a }}
        assertEquals(TokenType.EXPR_START, tokens.get(0).type());
        assertEquals(TokenType.KEYWORD_IF, tokens.get(1).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).type());
        assertEquals("a", tokens.get(2).value());
        assertEquals(TokenType.EXPR_END, tokens.get(3).type());
        // 1
        assertEquals(TokenType.HTML, tokens.get(4).type());
        // {{ else if b }}
        assertEquals(TokenType.EXPR_START, tokens.get(5).type());
        assertEquals(TokenType.KEYWORD_ELSE, tokens.get(6).type());
        assertEquals(TokenType.KEYWORD_IF, tokens.get(7).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(8).type());
        assertEquals("b", tokens.get(8).value());
        assertEquals(TokenType.EXPR_END, tokens.get(9).type());
    }

    @Test
    void testMethodCallExpression() {
        String source = """
                {{ post.getTitle() }}
                """;
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
    void testMultipleDirectives() {
        String source = """
                @page "/posts"
                @inject PostService posts
                @inject Auth auth

                @init {
                  allPosts = posts.findAll();
                }

                <div>{{ allPosts }}</div>
                """;
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.PAGE, tokens.get(0).type());
        assertEquals(TokenType.STRING_LITERAL, tokens.get(1).type());
        assertEquals(TokenType.INJECT, tokens.get(2).type());
        assertEquals(TokenType.INJECT, tokens.get(5).type());
        assertEquals(TokenType.INIT, tokens.get(8).type());
        assertEquals(TokenType.CODE_BLOCK, tokens.get(9).type());
    }
}
