package candi.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CandiCompilerTest {

    private final CandiCompiler compiler = new CandiCompiler();

    @Test
    void testSimplePage() {
        String source = """
                @Page("/hello")
                public class HelloPage {
                }

                <template>
                <h1>Hello World</h1>
                </template>
                """;

        String java = compiler.compile(source, "hello.jhtml", "pages", "HelloPage");

        assertTrue(java.contains("class HelloPage implements CandiPage"));
        assertTrue(java.contains("@CandiRoute(path = \"/hello\""));
        assertTrue(java.contains("out.append(\"<h1>Hello World</h1>"));
    }

    @Test
    void testPageWithAutowiredFieldAndExpression() {
        String source = """
                @Page("/greet")
                public class GreetPage {

                    @Autowired
                    private GreetService greet;
                }

                <template>
                <h1>{{ greet.getMessage() }}</h1>
                </template>
                """;

        String java = compiler.compile(source, "greet.jhtml", "pages", "GreetPage");

        assertTrue(java.contains("@Autowired"));
        assertTrue(java.contains("private GreetService greet;"));
        assertTrue(java.contains("out.append(\"<h1>\");"));
        assertTrue(java.contains("out.appendEscaped(String.valueOf(this.greet.getMessage()));"));
        assertTrue(java.contains("out.append(\"</h1>"));
    }

    @Test
    void testPageWithInitMethod() {
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
                <ul>{{ for post in allPosts }}<li>{{ post.title }}</li>{{ end }}</ul>
                </template>
                """;

        String java = compiler.compile(source, "posts.jhtml", "pages", "PostsPage");

        assertTrue(java.contains("private List<Post> allPosts;"));
        assertTrue(java.contains("public void init()"));
        assertTrue(java.contains("for (int post_index = 0;"));
        assertTrue(java.contains("var post = _list_post.get(post_index);"));
        assertTrue(java.contains("out.appendEscaped(String.valueOf(post.getTitle()));"));
    }

    @Test
    void testPageWithPostAction() {
        String source = """
                @Page("/submit")
                public class SubmitPage {

                    @Autowired
                    private FormService forms;

                    @Autowired
                    private RequestContext ctx;

                    @Post
                    public ActionResult create() {
                        forms.save(ctx.form("name"));
                        return ActionResult.redirect("/thanks");
                    }
                }

                <template>
                <form method="POST"><button>Submit</button></form>
                </template>
                """;

        String java = compiler.compile(source, "submit.jhtml", "pages", "SubmitPage");

        assertTrue(java.contains("@CandiRoute(path = \"/submit\""));
        assertTrue(java.contains("methods = {\"GET\", \"POST\"}"));
        assertTrue(java.contains("@Post"));
        assertTrue(java.contains("public ActionResult create()"));
    }

    @Test
    void testPageWithMultipleActions() {
        String source = """
                @Page("/item/{id}")
                public class ItemPage {

                    @Post
                    public ActionResult update() {
                        return ActionResult.redirect("/item");
                    }

                    @Delete
                    public ActionResult remove() {
                        return ActionResult.redirect("/items");
                    }
                }

                <template>
                <div>item</div>
                </template>
                """;

        String java = compiler.compile(source, "item.jhtml", "pages", "ItemPage");

        assertTrue(java.contains("methods = {\"GET\", \"POST\", \"DELETE\"}"));
        assertTrue(java.contains("@Post"));
        assertTrue(java.contains("@Delete"));
    }

    @Test
    void testIfElseBlock() {
        String source = "{{ if visible }}<p>yes</p>{{ else }}<p>no</p>{{ end }}";

        String java = compiler.compile(source, "test.jhtml", "pages", "Test__Page");

        assertTrue(java.contains("if ("));
        assertTrue(java.contains("out.append(\"<p>yes</p>\");"));
        assertTrue(java.contains("} else {"));
        assertTrue(java.contains("out.append(\"<p>no</p>\");"));
    }

    @Test
    void testElseIfBlock() {
        String source = "{{ if a }}1{{ else if b }}2{{ else }}3{{ end }}";

        String java = compiler.compile(source, "test.jhtml", "pages", "Test__Page");

        assertTrue(java.contains("} else {"));
    }

    @Test
    void testForLoop() {
        String source = "<ul>{{ for item in items }}<li>{{ item }}</li>{{ end }}</ul>";

        String java = compiler.compile(source, "test.jhtml", "pages", "Test__Page");

        assertTrue(java.contains("for (int item_index = 0;"));
        assertTrue(java.contains("var item = _list_item.get(item_index);"));
        assertTrue(java.contains("out.appendEscaped(String.valueOf(item));"));
    }

    @Test
    void testPropertyAccess() {
        String source = """
                public class TestPage {
                    private Post post;
                }

                <template>
                {{ post.title }}
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "TestPage");

        assertTrue(java.contains("this.post.getTitle()"));
    }

    @Test
    void testNullSafePropertyAccess() {
        String source = """
                public class TestPage {
                    private Post post;
                }

                <template>
                {{ post?.title }}
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "TestPage");

        assertTrue(java.contains("this.post == null ? null : this.post.getTitle()"));
    }

    @Test
    void testRawExpression() {
        String source = """
                public class TestPage {
                    private Post post;
                }

                <template>
                {{ raw post.htmlContent }}
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "TestPage");

        assertTrue(java.contains("out.append(String.valueOf("));
    }

    @Test
    void testEqualityUsesObjectsEquals() {
        String source = "{{ if status == \"active\" }}<span>active</span>{{ end }}";

        String java = compiler.compile(source, "test.jhtml", "pages", "Test__Page");

        assertTrue(java.contains("Objects.equals("));
    }

    @Test
    void testDeriveClassName() {
        // Legacy .page.html support
        assertEquals("Index__Page", CandiCompiler.deriveClassName("index.page.html"));
        assertEquals("PostEdit__Page", CandiCompiler.deriveClassName("post-edit.page.html"));

        // New .jhtml extension
        assertEquals("Index__Page", CandiCompiler.deriveClassName("index.jhtml"));
        assertEquals("PostEdit__Page", CandiCompiler.deriveClassName("post-edit.jhtml"));
        assertEquals("UserProfile__Page", CandiCompiler.deriveClassName("user-profile.jhtml"));
        assertEquals("Dashboard__Page", CandiCompiler.deriveClassName("dashboard.jhtml"));
    }

    @Test
    void testFullPage() {
        String source = """
                @Page(value = "/post/{id}/edit", layout = "base")
                public class PostEditPage {

                    @Autowired
                    private PostService posts;

                    @Autowired
                    private RequestContext ctx;

                    @Autowired
                    private Auth auth;

                    private Post post;
                    private boolean canEdit;

                    public void init() {
                        post = posts.getById(ctx.path("id"));
                        canEdit = auth.isAdmin();
                    }

                    @Post
                    public ActionResult update() {
                        posts.update(ctx.path("id"), ctx.form("title"));
                        return ActionResult.redirect("/posts");
                    }

                    @Delete
                    public ActionResult remove() {
                        posts.delete(ctx.path("id"));
                        return ActionResult.redirect("/posts");
                    }
                }

                <template>
                <!DOCTYPE html>
                <html>
                  <body>
                    <h1>{{ post.title }}</h1>
                    {{ if post.published }}
                      <article>{{ post.content }}</article>
                    {{ end }}
                    {{ if canEdit }}
                      <form method="POST">
                        <input name="title" value="{{ post.title }}">
                        <button>Save</button>
                      </form>
                    {{ end }}
                  </body>
                </html>
                </template>
                """;

        String java = compiler.compile(source, "post-edit.jhtml", "pages", "PostEditPage");

        assertTrue(java.contains("class PostEditPage implements CandiPage"));
        assertTrue(java.contains("@CandiRoute(path = \"/post/{id}/edit\""));
        assertTrue(java.contains("private PostService posts;"));
        assertTrue(java.contains("private RequestContext ctx;"));
        assertTrue(java.contains("private Auth auth;"));
        assertTrue(java.contains("public void init()"));
        assertTrue(java.contains("@Post"));
        assertTrue(java.contains("@Delete"));
        assertTrue(java.contains("post.getTitle()"));
        assertTrue(java.contains("post.getPublished()"));
        assertTrue(java.contains("post.getContent()"));
        // Layout injection
        assertTrue(java.contains("private CandiLayout baseLayout;"));
        assertTrue(java.contains("baseLayout.render(out,"));

        System.out.println("=== Generated Java ===");
        System.out.println(java);
    }

    @Test
    void testBooleanExpressionInIf() {
        String source = "{{ if a && b }}<p>both</p>{{ end }}";

        String java = compiler.compile(source, "test.jhtml", "pages", "Test__Page");

        assertTrue(java.contains("&&"));
    }

    @Test
    void testNestedForLoops() {
        String source = """
                {{ for group in groups }}
                  <h2>{{ group.name }}</h2>
                  {{ for item in group.items }}
                    <p>{{ item.title }}</p>
                  {{ end }}
                {{ end }}
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "Test__Page");

        assertTrue(java.contains("for (int group_index = 0;"));
        assertTrue(java.contains("var group = _list_group.get(group_index);"));
        assertTrue(java.contains("for (int item_index = 0;"));
        assertTrue(java.contains("var item = _list_item.get(item_index);"));
    }

    @Test
    void testIncludeGeneration() {
        String source = """
                <template>
                {{ include "header" title="Home" }}
                <p>Content</p>
                {{ include "footer" }}
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "Test__Page");

        assertTrue(java.contains("include \"header\""));
        assertTrue(java.contains("include \"footer\""));
    }

    @Test
    void testContentPlaceholder() {
        String source = """
                @Layout
                public class BaseLayout {
                }

                <template>
                <html>
                <body>
                {{ content }}
                </body>
                </html>
                </template>
                """;

        String java = compiler.compile(source, "base.jhtml", "layouts", "BaseLayout");

        assertTrue(java.contains("implements CandiLayout"));
        assertTrue(java.contains("@Component(\"baseLayout\")"));
        assertTrue(java.contains("slots.renderSlot(\"content\", out);"));
    }

    @Test
    void testLayoutAnnotationOnPage() {
        String source = """
                @Page(value = "/about", layout = "base")
                public class AboutPage {
                }

                <template>
                <h1>About</h1>
                </template>
                """;

        String java = compiler.compile(source, "about.jhtml", "pages", "AboutPage");

        assertTrue(java.contains("private CandiLayout baseLayout;"));
        assertTrue(java.contains("baseLayout.render(out,"));
    }

    @Test
    void testWidgetCall() {
        String source = """
                <template>
                {{ widget "alert" type="error" message="Oops" }}
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "Test__Page");

        assertTrue(java.contains("CandiComponent _comp"));
        assertTrue(java.contains("Alert__Widget"));
        assertTrue(java.contains("_params.put(\"type\""));
        assertTrue(java.contains("_params.put(\"message\""));
    }

    @Test
    void testLegacyComponentCallStillWorks() {
        String source = """
                <template>
                {{ component "alert" type="error" message="Oops" }}
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "Test__Page");

        assertTrue(java.contains("CandiComponent _comp"));
        assertTrue(java.contains("Alert__Widget"));
    }

    @Test
    void testBodyOnlyPage() {
        String source = "<h1>Simple</h1>";

        String java = compiler.compile(source, "simple.jhtml", "pages", "Simple__Page");

        assertTrue(java.contains("class Simple__Page implements CandiPage"));
        assertTrue(java.contains("out.append(\"<h1>Simple</h1>"));
    }

    @Test
    void testWidgetGeneration() {
        String source = """
                @Widget
                public class AlertWidget {
                    private String type;
                    private String message;
                }

                <template>
                <div class="alert alert-{{ type }}">{{ message }}</div>
                </template>
                """;

        String java = compiler.compile(source, "alert.jhtml", "widgets", "AlertWidget");

        assertTrue(java.contains("implements CandiComponent"));
        assertTrue(java.contains("@Component(\"AlertWidget__Widget\")"));
        assertTrue(java.contains("@Scope(\"prototype\")"));
        assertTrue(java.contains("public void setParams(java.util.Map<String, Object> params)"));
        assertTrue(java.contains("this.type = (String) params.get(\"type\")"));
        assertTrue(java.contains("this.message = (String) params.get(\"message\")"));
        assertTrue(java.contains("public void render(HtmlOutput out)"));

        System.out.println("=== Generated Widget ===");
        System.out.println(java);
    }

    @Test
    void testLayoutGeneration() {
        String source = """
                @Layout
                public class BaseLayout {
                }

                <template>
                <!DOCTYPE html>
                <html>
                <head><title>My App</title></head>
                <body>
                {{ content }}
                </body>
                </html>
                </template>
                """;

        String java = compiler.compile(source, "base.jhtml", "layouts", "BaseLayout");

        assertTrue(java.contains("implements CandiLayout"));
        assertTrue(java.contains("@Component(\"baseLayout\")"));
        assertFalse(java.contains("@Scope")); // layouts are singleton (default)
        assertTrue(java.contains("public void render(HtmlOutput out, SlotProvider slots)"));
        assertTrue(java.contains("slots.renderSlot(\"content\", out)"));

        System.out.println("=== Generated Layout ===");
        System.out.println(java);
    }

    // ========== @Template annotation format ==========

    @Test
    void testTemplateAnnotation() {
        String source = """
                @Page("/hello")
                @Template(\"""
                <h1>Hello World</h1>
                \""")
                public class HelloPage {
                }
                """;

        String java = compiler.compile(source, "HelloPage.java", "pages", "HelloPage");

        assertTrue(java.contains("class HelloPage implements CandiPage"));
        assertTrue(java.contains("@CandiRoute(path = \"/hello\""));
        assertTrue(java.contains("out.append(\"<h1>Hello World</h1>"));
        assertFalse(java.contains("@Template"));
    }

    @Test
    void testTemplateAnnotationWithExpressions() {
        String source = """
                @Page("/greet")
                @Template(\"""
                <h1>{{ name }}</h1>
                <p>{{ message }}</p>
                \""")
                public class GreetPage {
                    private String name;
                    private String message;
                }
                """;

        String java = compiler.compile(source, "GreetPage.java", "pages", "GreetPage");

        assertTrue(java.contains("class GreetPage implements CandiPage"));
        assertTrue(java.contains("out.appendEscaped(String.valueOf(this.name));"));
        assertTrue(java.contains("out.appendEscaped(String.valueOf(this.message));"));
        assertFalse(java.contains("@Template"));
    }

    @Test
    void testTemplateAnnotationLayout() {
        String source = """
                @Layout
                @Template(\"""
                <html><body>{{ content }}</body></html>
                \""")
                public class BaseLayout {
                }
                """;

        String java = compiler.compile(source, "BaseLayout.java", "layouts", "BaseLayout");

        assertTrue(java.contains("implements CandiLayout"));
        assertTrue(java.contains("@Component(\"baseLayout\")"));
        assertTrue(java.contains("slots.renderSlot(\"content\", out)"));
        assertFalse(java.contains("@Template"));
    }

    @Test
    void testFragmentInlineRendering() {
        String source = """
                <h1>Posts</h1>
                {{ fragment "post-list" }}<ul><li>item</li></ul>{{ end }}
                <footer>done</footer>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "Test__Page");

        // Fragment renders inline in the main render() method
        assertTrue(java.contains("out.append(\"<h1>Posts</h1>"));
        assertTrue(java.contains("out.append(\"<ul><li>item</li></ul>"));
        assertTrue(java.contains("out.append(\"\\n<footer>done</footer>"));

        // renderFragment dispatch method is generated
        assertTrue(java.contains("public void renderFragment(String _name, HtmlOutput out)"));
        assertTrue(java.contains("case \"post-list\""));
        assertTrue(java.contains("renderFragment_post_list(out)"));

        // Per-fragment private method is generated
        assertTrue(java.contains("private void renderFragment_post_list(HtmlOutput out)"));
    }

    @Test
    void testFragmentWithExpressions() {
        String source = """
                public class PostsPage {
                    private List<Post> posts;
                }

                <template>
                {{ fragment "post-list" }}{{ for p in posts }}<li>{{ p.title }}</li>{{ end }}{{ end }}
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "PostsPage");

        // Both render() and renderFragment_post_list() should contain the loop
        assertTrue(java.contains("renderFragment_post_list(out)"));
        assertTrue(java.contains("private void renderFragment_post_list(HtmlOutput out)"));
        // The fragment method body should iterate over posts
        assertTrue(java.contains("for (int p_index = 0;"));
        assertTrue(java.contains("var p = _list_p.get(p_index);"));
        assertTrue(java.contains("p.getTitle()"));
    }

    @Test
    void testTemplateAnnotationWidget() {
        String source = """
                @Widget
                @Template(\"""
                <div class="alert">{{ message }}</div>
                \""")
                public class AlertWidget {
                    private String message;
                }
                """;

        String java = compiler.compile(source, "AlertWidget.java", "widgets", "AlertWidget");

        assertTrue(java.contains("implements CandiComponent"));
        assertTrue(java.contains("@Component(\"AlertWidget__Widget\")"));
        assertTrue(java.contains("out.appendEscaped(String.valueOf(this.message));"));
        assertFalse(java.contains("@Template"));
    }

    // ========== Phase 1: Comments, Whitespace, Verbatim ==========

    @Test
    void testTemplateComment() {
        String source = "<h1>Hello</h1>{{-- this is hidden --}}<p>World</p>";

        String java = compiler.compile(source, "test.jhtml", "pages", "Test__Page");

        assertTrue(java.contains("<h1>Hello</h1>"));
        assertTrue(java.contains("<p>World</p>"));
        assertFalse(java.contains("this is hidden"), "Comment should be stripped");
    }

    @Test
    void testWhitespaceControl() {
        String source = """
                public class TestPage {
                    private String name;
                }

                <template>
                <p>
                {{- name -}}
                </p>
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "TestPage");

        // The generated code should show trimmed whitespace
        assertTrue(java.contains("this.name"), "Should access field");
    }

    @Test
    void testVerbatimBlock() {
        String source = "before{{ verbatim }}<div>{{ not.parsed }}</div>{{ end }}after";

        String java = compiler.compile(source, "test.jhtml", "pages", "Test__Page");

        assertTrue(java.contains("{{ not.parsed }}"), "Verbatim content should be raw HTML");
        assertFalse(java.contains("getNotParsed"), "Should NOT parse expressions inside verbatim");
    }

    // ========== Phase 2: Ternary, Null Coalescing, Loop Metadata ==========

    @Test
    void testTernaryExpression() {
        String source = """
                public class TestPage {
                    private boolean active;
                }

                <template>
                <span>{{ active ? "Yes" : "No" }}</span>
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "TestPage");

        assertTrue(java.contains("?"), "Should contain ternary");
        assertTrue(java.contains("\"Yes\""));
        assertTrue(java.contains("\"No\""));
    }

    @Test
    void testNullCoalescing() {
        String source = """
                public class TestPage {
                    private String name;
                }

                <template>
                <p>{{ name ?? "Guest" }}</p>
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "TestPage");

        assertTrue(java.contains("this.name"), "Should access field");
        assertTrue(java.contains("!= null"), "Should null-check");
        assertTrue(java.contains("\"Guest\""), "Should have fallback value");
    }

    @Test
    void testLoopMetadata() {
        String source = """
                public class TestPage {
                    private java.util.List<String> items;
                }

                <template>
                {{ for item in items }}
                {{ if item_first }}<ul>{{ end }}
                <li>{{ item }}</li>
                {{ if item_last }}</ul>{{ end }}
                {{ end }}
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "TestPage");

        assertTrue(java.contains("int item_index = 0;"), "Should have item_index");
        assertTrue(java.contains("boolean item_first = (item_index == 0);"), "Should have item_first");
        assertTrue(java.contains("boolean item_last = (item_index == _list_item.size() - 1);"), "Should have item_last");
    }

    // ========== Phase 3: Arithmetic, String Concat, Unary Minus ==========

    @Test
    void testArithmeticOperators() {
        String source = """
                public class TestPage {
                    private int a;
                    private int b;
                }

                <template>
                <p>{{ a + b }}</p>
                <p>{{ a * b }}</p>
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "TestPage");

        assertTrue(java.contains("+"), "Should contain addition");
        assertTrue(java.contains("*"), "Should contain multiplication");
    }

    @Test
    void testStringConcatenation() {
        String source = """
                public class TestPage {
                    private String first;
                    private String last;
                }

                <template>
                <p>{{ first ~ " " ~ last }}</p>
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "TestPage");

        assertTrue(java.contains("String.valueOf"), "Should use String.valueOf for concat");
    }

    @Test
    void testUnaryMinus() {
        String source = """
                public class TestPage {
                    private double amount;
                }

                <template>
                <p>{{ -amount }}</p>
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "TestPage");

        assertTrue(java.contains("-("), "Should contain unary minus");
    }

    // ========== Phase 4: Filters, Index Access ==========

    @Test
    void testFilterExpression() {
        String source = """
                public class TestPage {
                    private String name;
                }

                <template>
                <p>{{ name | upper }}</p>
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "TestPage");

        assertTrue(java.contains("candi.runtime.CandiFilters.upper("), "Should call CandiFilters.upper");
    }

    @Test
    void testFilterWithArgument() {
        String source = """
                public class TestPage {
                    private String bio;
                }

                <template>
                <p>{{ bio | truncate(200) }}</p>
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "TestPage");

        assertTrue(java.contains("candi.runtime.CandiFilters.truncate("), "Should call CandiFilters.truncate");
        assertTrue(java.contains("200"), "Should pass argument");
    }

    @Test
    void testIndexAccess() {
        String source = """
                public class TestPage {
                    private java.util.List<String> items;
                }

                <template>
                <p>{{ items[0] }}</p>
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "TestPage");

        assertTrue(java.contains("candi.runtime.CandiRuntime.index("), "Should call CandiRuntime.index");
    }

    // ========== Phase 5: Set, Switch ==========

    @Test
    void testSetVariable() {
        String source = """
                <template>
                {{ set greeting = "Hello World" }}
                <p>{{ greeting }}</p>
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "Test__Page");

        assertTrue(java.contains("var greeting = \"Hello World\";"), "Should generate local variable");
    }

    @Test
    void testSwitchCase() {
        String source = """
                public class TestPage {
                    private String role;
                }

                <template>
                {{ switch role }}
                {{ case "admin" }}<span>Administrator</span>
                {{ case "user" }}<span>Regular User</span>
                {{ default }}<span>Guest</span>
                {{ end }}
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "TestPage");

        assertTrue(java.contains("Objects.equals("), "Should use Objects.equals");
        assertTrue(java.contains("\"admin\""), "Should have admin case");
        assertTrue(java.contains("\"user\""), "Should have user case");
        assertTrue(java.contains("} else {"), "Should have default branch");
    }

    // ========== Phase 6: Named Slots, Asset Stacking ==========

    @Test
    void testLayoutWithSlot() {
        String source = """
                @Layout
                public class MainLayout {
                }

                <template>
                <html>
                <body>
                <div class="sidebar">{{ slot "sidebar" }}<p>Default sidebar</p>{{ end }}</div>
                <div class="content">{{ content }}</div>
                </body>
                </html>
                </template>
                """;

        String java = compiler.compile(source, "main.jhtml", "layouts", "MainLayout");

        assertTrue(java.contains("implements CandiLayout"));
        assertTrue(java.contains("slots.renderSlot(\"sidebar\", out)"), "Should render named slot");
        assertTrue(java.contains("slots.renderSlot(\"content\", out)"), "Should render content slot");
    }

    @Test
    void testPageWithBlock() {
        String source = """
                @Page(value = "/test", layout = "main")
                public class TestPage {
                }

                <template>
                <h1>Content</h1>
                {{ block "sidebar" }}<nav>Custom Sidebar</nav>{{ end }}
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "TestPage");

        assertTrue(java.contains("mainLayout.render(out,"), "Should call layout.render");
        assertTrue(java.contains("\"sidebar\""), "Should dispatch sidebar block");
        assertTrue(java.contains("\"content\""), "Should dispatch content");
    }

    @Test
    void testAssetStacking() {
        String source = """
                @Layout
                public class BaseLayout {
                }

                <template>
                <html>
                <body>{{ content }}</body>
                {{ stack "scripts" }}
                </html>
                </template>
                """;

        String java = compiler.compile(source, "base.jhtml", "layouts", "BaseLayout");

        assertTrue(java.contains("out.renderStack(\"scripts\")"), "Should render stack in layout");
    }

    @Test
    void testPushToStack() {
        String source = """
                <template>
                <h1>Page</h1>
                {{ push "scripts" }}<script src="app.js"></script>{{ end }}
                </template>
                """;

        String java = compiler.compile(source, "test.jhtml", "pages", "Test__Page");

        assertTrue(java.contains("pushStack(\"scripts\""), "Should push to scripts stack");
    }
}
