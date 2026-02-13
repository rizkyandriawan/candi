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

        assertTrue(java.contains("for (int post_index = 0;"),
                "for loop should use indexed iteration");
        assertTrue(java.contains("var post = _list_post.get(post_index);"),
                "loop variable should be extracted from list");
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

    // ========== FRAGMENT Tests ==========

    @Test
    void testPageWithFragment() {
        BodyNode body = parseTemplate("<h1>Posts</h1>{{ fragment \"post-list\" }}<ul><li>item</li></ul>{{ end }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "PostsPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/posts", null,
                Set.of(), Map.of(), Set.of(),
                body));

        // Inline rendering in render()
        assertTrue(java.contains("out.append(\"<h1>Posts</h1>\");"));
        assertTrue(java.contains("out.append(\"<ul><li>item</li></ul>\");"));

        // renderFragment dispatch
        assertTrue(java.contains("public void renderFragment(String _name, HtmlOutput out)"));
        assertTrue(java.contains("case \"post-list\" -> renderFragment_post_list(out);"));
        assertTrue(java.contains("default -> throw new IllegalArgumentException(\"Unknown fragment: \" + _name);"));

        // Per-fragment method
        assertTrue(java.contains("private void renderFragment_post_list(HtmlOutput out)"));
    }

    @Test
    void testPageWithFragmentUsesGetters() {
        BodyNode body = parseTemplate("{{ fragment \"results\" }}<p>{{ title }}</p>{{ end }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "SearchPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/search", null,
                Set.of("title"), Map.of("title", "String"), Set.of(),
                body));

        // Fragment method should use getter access
        assertTrue(java.contains("this.getTitle()"), "Fragment should use getter for field access");
    }

    @Test
    void testPageWithMultipleFragments() {
        BodyNode body = parseTemplate(
                "{{ fragment \"header\" }}<h1>Title</h1>{{ end }}" +
                "{{ fragment \"body\" }}<p>Content</p>{{ end }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of(), Map.of(), Set.of(),
                body));

        // Both fragments in switch
        assertTrue(java.contains("case \"header\" -> renderFragment_header(out);"));
        assertTrue(java.contains("case \"body\" -> renderFragment_body(out);"));

        // Both private methods
        assertTrue(java.contains("private void renderFragment_header(HtmlOutput out)"));
        assertTrue(java.contains("private void renderFragment_body(HtmlOutput out)"));
    }

    @Test
    void testPageWithNoFragmentsDoesNotGenerateFragmentMethods() {
        BodyNode body = parseTemplate("<h1>No fragments here</h1>");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "SimplePage", "pages", JavaAnalyzer.FileType.PAGE,
                "/simple", null,
                Set.of(), Map.of(), Set.of(),
                body));

        assertFalse(java.contains("renderFragment"), "Should not generate fragment methods when no fragments");
    }

    @Test
    void testFragmentNameWithHyphensConvertsToUnderscores() {
        BodyNode body = parseTemplate("{{ fragment \"search-results\" }}<div>results</div>{{ end }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of(), Map.of(), Set.of(),
                body));

        assertTrue(java.contains("renderFragment_search_results(out)"));
        assertTrue(java.contains("private void renderFragment_search_results(HtmlOutput out)"));
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

    // ========== Loop Metadata Tests ==========

    @Test
    void testForLoopMetadata() {
        BodyNode body = parseTemplate(
                "{{ for item in items }}" +
                "{{ if item_first }}<strong>{{ end }}" +
                "<li>{{ item }}</li>" +
                "{{ if item_last }}</ul>{{ end }}" +
                "{{ end }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of("items"), Map.of("items", "List<String>"), Set.of(),
                body));

        assertTrue(java.contains("for (int item_index = 0;"), "Should generate indexed loop");
        assertTrue(java.contains("boolean item_first = (item_index == 0);"), "Should generate item_first");
        assertTrue(java.contains("boolean item_last = (item_index == _list_item.size() - 1);"), "Should generate item_last");
    }

    // ========== Ternary Expression Tests ==========

    @Test
    void testTernaryExpression() {
        BodyNode body = parseTemplate("{{ active ? \"yes\" : \"no\" }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of("active"), Map.of("active", "boolean"), Set.of(),
                body));

        assertTrue(java.contains("?"), "Should contain ternary operator");
        assertTrue(java.contains(":"), "Should contain ternary colon");
        assertTrue(java.contains("\"yes\""));
        assertTrue(java.contains("\"no\""));
    }

    // ========== Null Coalescing Tests ==========

    @Test
    void testNullCoalescing() {
        BodyNode body = parseTemplate("{{ name ?? \"Guest\" }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of("name"), Map.of("name", "String"), Set.of(),
                body));

        assertTrue(java.contains("this.getName()"), "Should use getter");
        assertTrue(java.contains("!= null"), "Should null-check");
        assertTrue(java.contains("\"Guest\""), "Should have fallback");
    }

    // ========== Arithmetic Tests ==========

    @Test
    void testArithmeticExpression() {
        BodyNode body = parseTemplate("{{ a + b }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of("a", "b"), Map.of("a", "int", "b", "int"), Set.of(),
                body));

        assertTrue(java.contains("this.getA()"), "Should use getter for a");
        assertTrue(java.contains("this.getB()"), "Should use getter for b");
        assertTrue(java.contains("+"), "Should contain plus operator");
    }

    // ========== String Concatenation Tests ==========

    @Test
    void testStringConcat() {
        BodyNode body = parseTemplate("{{ first ~ \" \" ~ last }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of("first", "last"), Map.of("first", "String", "last", "String"), Set.of(),
                body));

        assertTrue(java.contains("String.valueOf"), "String concat should use String.valueOf");
    }

    // ========== Unary Minus Tests ==========

    @Test
    void testUnaryMinus() {
        BodyNode body = parseTemplate("{{ -amount }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of("amount"), Map.of("amount", "double"), Set.of(),
                body));

        assertTrue(java.contains("-("), "Should contain unary minus");
        assertTrue(java.contains("this.getAmount()"), "Should use getter");
    }

    // ========== Filter Tests ==========

    @Test
    void testSimpleFilter() {
        BodyNode body = parseTemplate("{{ name | upper }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of("name"), Map.of("name", "String"), Set.of(),
                body));

        assertTrue(java.contains("candi.runtime.CandiFilters.upper("), "Should call CandiFilters.upper");
        assertTrue(java.contains("this.getName()"), "Should use getter as filter input");
    }

    @Test
    void testFilterWithArgs() {
        BodyNode body = parseTemplate("{{ text | truncate(100) }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of("text"), Map.of("text", "String"), Set.of(),
                body));

        assertTrue(java.contains("candi.runtime.CandiFilters.truncate("), "Should call CandiFilters.truncate");
        assertTrue(java.contains("100"), "Should pass truncate argument");
    }

    @Test
    void testChainedFilters() {
        BodyNode body = parseTemplate("{{ name | trim | upper }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of("name"), Map.of("name", "String"), Set.of(),
                body));

        assertTrue(java.contains("candi.runtime.CandiFilters.upper(candi.runtime.CandiFilters.trim("),
                "Should chain filters");
    }

    // ========== Index Access Tests ==========

    @Test
    void testIndexAccessList() {
        BodyNode body = parseTemplate("{{ items[0] }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of("items"), Map.of("items", "List<String>"), Set.of(),
                body));

        assertTrue(java.contains("candi.runtime.CandiRuntime.index("), "Should use CandiRuntime.index");
        assertTrue(java.contains("this.getItems()"), "Should use getter");
    }

    @Test
    void testIndexAccessMap() {
        BodyNode body = parseTemplate("{{ config[\"key\"] }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of("config"), Map.of("config", "Map<String,String>"), Set.of(),
                body));

        assertTrue(java.contains("candi.runtime.CandiRuntime.index("), "Should use CandiRuntime.index");
        assertTrue(java.contains("\"key\""), "Should pass string key");
    }

    // ========== Set Variable Tests ==========

    @Test
    void testSetVariable() {
        BodyNode body = parseTemplate("{{ set greeting = \"Hello\" }}<p>{{ greeting }}</p>");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of(), Map.of(), Set.of(),
                body));

        assertTrue(java.contains("var greeting = \"Hello\";"), "Should generate local variable");
        assertTrue(java.contains("greeting"), "Should use the variable");
    }

    // ========== Switch/Case Tests ==========

    @Test
    void testSwitchCase() {
        BodyNode body = parseTemplate(
                "{{ switch status }}" +
                "{{ case \"active\" }}<span class=\"green\">Active</span>" +
                "{{ case \"inactive\" }}<span class=\"red\">Inactive</span>" +
                "{{ default }}<span>Unknown</span>" +
                "{{ end }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of("status"), Map.of("status", "String"), Set.of(),
                body));

        assertTrue(java.contains("Objects.equals("), "Switch should use Objects.equals");
        assertTrue(java.contains("\"active\""), "Should check active case");
        assertTrue(java.contains("\"inactive\""), "Should check inactive case");
        assertTrue(java.contains("} else {"), "Should have default else branch");
    }

    // ========== Named Slot Tests ==========

    @Test
    void testLayoutWithNamedSlot() {
        BodyNode body = parseTemplate(
                "<html><body>{{ content }}<aside>{{ slot \"sidebar\" }}<p>default sidebar</p>{{ end }}</aside></body></html>");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "MainLayout", "layouts", JavaAnalyzer.FileType.LAYOUT,
                null, "main",
                Set.of(), Map.of(), Set.of(),
                body));

        assertTrue(java.contains("implements CandiLayout"), "Should implement CandiLayout");
        assertTrue(java.contains("slots.renderSlot(\"sidebar\", out)"), "Should render named slot");
    }

    @Test
    void testPageWithBlockForSlot() {
        BodyNode body = parseTemplate(
                "<h1>Content</h1>{{ block \"sidebar\" }}<nav>Custom Sidebar</nav>{{ end }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", "main",
                Set.of(), Map.of(), Set.of(),
                body));

        assertTrue(java.contains("mainLayout.render(out,"), "Should call layout render");
        assertTrue(java.contains("\"sidebar\""), "Should dispatch sidebar block");
    }

    // ========== Asset Stacking Tests ==========

    @Test
    void testStackRendering() {
        BodyNode body = parseTemplate(
                "<html><body>{{ content }}{{ stack \"scripts\" }}</body></html>");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "BaseLayout", "layouts", JavaAnalyzer.FileType.LAYOUT,
                null, "base",
                Set.of(), Map.of(), Set.of(),
                body));

        assertTrue(java.contains("out.renderStack(\"scripts\")"), "Should render stack");
    }

    @Test
    void testPushToStack() {
        BodyNode body = parseTemplate(
                "<h1>Page</h1>{{ push \"scripts\" }}<script src=\"app.js\"></script>{{ end }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of(), Map.of(), Set.of(),
                body));

        assertTrue(java.contains("pushStack(\"scripts\""), "Should push to stack");
    }

    // ========== Comment Tests (codegen) ==========

    @Test
    void testCommentNotInOutput() {
        BodyNode body = parseTemplate("<h1>Hello</h1>{{-- comment --}}<p>World</p>");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of(), Map.of(), Set.of(),
                body));

        assertFalse(java.contains("comment"), "Comment should not appear in generated code");
        assertTrue(java.contains("<h1>Hello</h1>"), "HTML before comment should remain");
        assertTrue(java.contains("<p>World</p>"), "HTML after comment should remain");
    }

    // ========== Verbatim Tests (codegen) ==========

    @Test
    void testVerbatimOutput() {
        BodyNode body = parseTemplate("{{ verbatim }}<p>{{ raw_mustache }}</p>{{ end }}");

        String java = generate(new SubclassCodeGenerator.SubclassInput(
                "TestPage", "pages", JavaAnalyzer.FileType.PAGE,
                "/test", null,
                Set.of(), Map.of(), Set.of(),
                body));

        assertTrue(java.contains("{{ raw_mustache }}"), "Verbatim content should preserve {{ }}");
    }
}
