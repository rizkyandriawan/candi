package candi.compiler.codegen;

import candi.compiler.JavaAnalyzer;
import candi.compiler.ast.BodyNode;
import candi.compiler.lexer.Lexer;
import candi.compiler.lexer.Token;
import candi.compiler.parser.Parser;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SubclassCodeGeneratorTest {

    private BodyNode parseTemplate(String template) {
        List<Token> tokens = Lexer.tokenizeTemplate(template, "test.java");
        return new Parser(tokens, "test.java").parseBody();
    }

    private String generate(SubclassCodeGenerator.SubclassInput input) {
        return new SubclassCodeGenerator(input).generate();
    }

    // ========== PAGE Tests ==========

    @Test
    void testSimplePage() {
        BodyNode body = parseTemplate("<h1>Hello World</h1>");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "HelloPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/hello", null,
                Set.of(), Map.of(), Set.of(),
                body));

        assertTrue(java.contains("class HelloPage_Candi extends HelloPage implements CandiPage"));
        assertTrue(java.contains("@Component"));
        assertTrue(java.contains("@Scope(WebApplicationContext.SCOPE_REQUEST)"));
        assertTrue(java.contains("@CandiRoute(path = \"/hello\""));
        assertTrue(java.contains("out.append(\"<h1>Hello World</h1>"));
    }

    @Test
    void testPageFieldAccessUsesGetters() {
        BodyNode body = parseTemplate("<h1>{{ title }}</h1>");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "GreetPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/greet", null,
                Set.of("title"), Map.of("title", "String"), Set.of(),
                body));

        assertTrue(java.contains("this.getTitle()"), "Should use getter for field access");
        assertFalse(java.contains("this.title"), "Should NOT use direct field access");
    }

    @Test
    void testPageWithMultipleFields() {
        BodyNode body = parseTemplate("<h1>{{ name }}</h1><p>{{ message }}</p>");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "GreetPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/greet", null,
                Set.of("name", "message"), Map.of("name", "String", "message", "String"), Set.of(),
                body));

        assertTrue(java.contains("this.getName()"));
        assertTrue(java.contains("this.getMessage()"));
    }

    @Test
    void testPageWithLayout() {
        BodyNode body = parseTemplate("<h1>About</h1>");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "AboutPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/about", "base",
                Set.of(), Map.of(), Set.of(),
                body));

        assertTrue(java.contains("private CandiLayout baseLayout;"));
        assertTrue(java.contains("baseLayout.render(out,"));
    }

    @Test
    void testPageWithActions() {
        BodyNode body = parseTemplate("<form method=\"POST\"><button>Submit</button></form>");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "SubmitPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/submit", null,
                Set.of(), Map.of(), new LinkedHashSet<>(List.of("POST", "DELETE")),
                body));

        assertTrue(java.contains("methods = {\"GET\", \"POST\", \"DELETE\"}"));
    }

    @Test
    void testPageExtendsUserClass() {
        BodyNode body = parseTemplate("<p>test</p>");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "PostsPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/posts", null,
                Set.of(), Map.of(), Set.of(),
                body));

        assertTrue(java.contains("extends PostsPage"));
        assertTrue(java.contains("implements CandiPage"));
        assertFalse(java.contains("private PostService"), "Should NOT copy user fields");
    }

    @Test
    void testPageForLoopWithGetterField() {
        BodyNode body = parseTemplate("{{ for post in allPosts }}<li>{{ post.title }}</li>{{ end }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "PostsPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/posts", null,
                Set.of("allPosts"), Map.of("allPosts", "List<Post>"), Set.of(),
                body));

        assertTrue(java.contains("for (var post : this.getAllPosts())"),
                "for-in collection should use getter");
        assertTrue(java.contains("post.getTitle()"));
    }

    @Test
    void testPagePropertyAccessOnField() {
        BodyNode body = parseTemplate("{{ post.title }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "PostPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/post", null,
                Set.of("post"), Map.of("post", "Post"), Set.of(),
                body));

        assertTrue(java.contains("this.getPost().getTitle()"),
                "Field access should use getter, property access should use getter");
    }

    @Test
    void testPageNullSafeAccess() {
        BodyNode body = parseTemplate("{{ post?.title }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "PostPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/post", null,
                Set.of("post"), Map.of("post", "Post"), Set.of(),
                body));

        assertTrue(java.contains("this.getPost() == null ? null : this.getPost().getTitle()"));
    }

    // ========== LAYOUT Tests ==========

    @Test
    void testLayoutGeneration() {
        BodyNode body = parseTemplate("<html><body>{{ content }}</body></html>");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "BaseLayout", "layouts", JavaAnalyzer.FileType.LAYOUT,
                null, "base",
                Set.of(), Map.of(), Set.of(),
                body));

        assertTrue(java.contains("class BaseLayout_Candi extends BaseLayout implements CandiLayout"));
        assertTrue(java.contains("@Component(\"baseLayout\")"));
        assertFalse(java.contains("@Scope"), "Layouts are singleton (default scope)");
        assertTrue(java.contains("public void render(HtmlOutput out, SlotProvider slots)"));
        assertTrue(java.contains("slots.renderSlot(\"content\", out)"));
    }

    // ========== WIDGET Tests ==========

    @Test
    void testWidgetGeneration() {
        BodyNode body = parseTemplate("<div class=\"alert alert-{{ type }}\">{{ message }}</div>");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "AlertWidget", "widgets", JavaAnalyzer.FileType.WIDGET,
                null, null,
                new LinkedHashSet<>(List.of("type", "message")),
                new LinkedHashMap<>(Map.of("type", "String", "message", "String")),
                Set.of(),
                body));

        assertTrue(java.contains("class AlertWidget_Candi extends AlertWidget implements CandiComponent"));
        assertTrue(java.contains("@Component(\"AlertWidget__Widget\")"));
        assertTrue(java.contains("@Scope(\"prototype\")"));
    }

    @Test
    void testWidgetSetParamsUsesSetters() {
        BodyNode body = parseTemplate("<div>{{ type }}</div>");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "AlertWidget", "widgets", JavaAnalyzer.FileType.WIDGET,
                null, null,
                new LinkedHashSet<>(List.of("type", "message")),
                new LinkedHashMap<>(Map.of("type", "String", "message", "String")),
                Set.of(),
                body));

        assertTrue(java.contains("public void setParams(java.util.Map<String, Object> params)"));
        assertTrue(java.contains("this.setType((String) params.get(\"type\"));"),
                "Widget setParams should use setter");
        assertTrue(java.contains("this.setMessage((String) params.get(\"message\"));"),
                "Widget setParams should use setter");
        assertFalse(java.contains("this.type ="), "Should NOT use direct field assignment");
        assertFalse(java.contains("this.message ="), "Should NOT use direct field assignment");
    }

    @Test
    void testWidgetRenderUsesGetters() {
        BodyNode body = parseTemplate("<div>{{ type }}: {{ message }}</div>");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "AlertWidget", "widgets", JavaAnalyzer.FileType.WIDGET,
                null, null,
                new LinkedHashSet<>(List.of("type", "message")),
                new LinkedHashMap<>(Map.of("type", "String", "message", "String")),
                Set.of(),
                body));

        assertTrue(java.contains("this.getType()"), "Widget render should use getter");
        assertTrue(java.contains("this.getMessage()"), "Widget render should use getter");
    }

    // ========== Component/Widget Call Tests ==========

    @Test
    void testPageWithWidgetCall() {
        BodyNode body = parseTemplate("{{ widget \"alert\" type=\"error\" }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of(), Map.of(), Set.of(),
                body));

        assertTrue(java.contains("ApplicationContext _applicationContext"));
        assertTrue(java.contains("Alert__Widget"));
        assertTrue(java.contains("_params.put(\"type\""));
    }

    // ========== Equality/Expression Tests ==========

    @Test
    void testEqualityUsesObjectsEquals() {
        BodyNode body = parseTemplate("{{ if status == \"active\" }}<span>active</span>{{ end }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of("status"), Map.of("status", "String"), Set.of(),
                body));

        assertTrue(java.contains("Objects.equals(this.getStatus(), \"active\")"));
    }

    @Test
    void testIfWithFieldUsingGetter() {
        BodyNode body = parseTemplate("{{ if canEdit }}<button>Edit</button>{{ end }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of("canEdit"), Map.of("canEdit", "boolean"), Set.of(),
                body));

        assertTrue(java.contains("this.getCanEdit()"), "if condition field should use getter");
    }

    @Test
    void testPackageDeclaration() {
        BodyNode body = parseTemplate("<p>test</p>");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "com.example.pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of(), Map.of(), Set.of(),
                body));

        assertTrue(java.contains("package com.example.pages;"));
    }
}
