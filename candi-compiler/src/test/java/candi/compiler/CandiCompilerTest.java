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
        assertTrue(java.contains("for (var post : this.allPosts)"));
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

        assertTrue(java.contains("for (var item : items)"));
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

        assertTrue(java.contains("for (var group : groups)"));
        assertTrue(java.contains("for (var item : group.getItems())"));
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
}
