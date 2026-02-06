package candi.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CandiCompilerTest {

    private final CandiCompiler compiler = new CandiCompiler();

    @Test
    void testSimplePage() {
        String source = """
                @page "/hello"

                <h1>Hello World</h1>
                """;

        String java = compiler.compile(source, "hello.page.html", "pages", "Hello__Page");

        assertTrue(java.contains("class Hello__Page implements CandiPage"));
        assertTrue(java.contains("@CandiRoute(path = \"/hello\""));
        assertTrue(java.contains("out.append(\"<h1>Hello World</h1>"));
    }

    @Test
    void testPageWithInjectAndExpression() {
        String source = """
                @page "/greet"
                @inject GreetService greet

                <h1>{{ greet.getMessage() }}</h1>
                """;

        String java = compiler.compile(source, "greet.page.html", "pages", "Greet__Page");

        assertTrue(java.contains("@Autowired"));
        assertTrue(java.contains("private GreetService greet;"));
        assertTrue(java.contains("out.append(\"<h1>\");"));
        assertTrue(java.contains("out.appendEscaped(String.valueOf(this.greet.getMessage()));"));
        assertTrue(java.contains("out.append(\"</h1>"));
    }

    @Test
    void testPageWithInit() {
        String source = """
                @page "/posts"
                @inject PostService posts

                @init {
                  allPosts = posts.findAll();
                }

                <ul>{{ for post in allPosts }}<li>{{ post.title }}</li>{{ end }}</ul>
                """;

        String java = compiler.compile(source, "posts.page.html", "pages", "Posts__Page");

        assertTrue(java.contains("private Object allPosts;"));
        assertTrue(java.contains("public void init()"));
        assertTrue(java.contains("this.allPosts = posts.findAll();"));
        assertTrue(java.contains("for (var post : this.allPosts)"));
        assertTrue(java.contains("out.appendEscaped(String.valueOf(post.getTitle()));"));
    }

    @Test
    void testPageWithAction() {
        String source = """
                @page "/submit"
                @inject FormService forms

                @action POST {
                  forms.save(ctx.form("name"));
                  redirect("/thanks");
                }

                <form method="POST"><button>Submit</button></form>
                """;

        String java = compiler.compile(source, "submit.page.html", "pages", "Submit__Page");

        assertTrue(java.contains("public ActionResult handleAction(String method)"));
        assertTrue(java.contains("if (\"POST\".equals(method))"));
        assertTrue(java.contains("return ActionResult.redirect(\"/thanks\");"));
        assertTrue(java.contains("return ActionResult.methodNotAllowed();"));
        assertTrue(java.contains("methods = {\"GET\", \"POST\"}"));
    }

    @Test
    void testPageWithMultipleActions() {
        String source = """
                @page "/item/{id}"

                @action POST {
                  redirect("/item");
                }

                @action DELETE {
                  redirect("/items");
                }

                <div>item</div>
                """;

        String java = compiler.compile(source, "item.page.html", "pages", "Item__Page");

        assertTrue(java.contains("if (\"POST\".equals(method))"));
        assertTrue(java.contains("if (\"DELETE\".equals(method))"));
        assertTrue(java.contains("methods = {\"GET\", \"POST\", \"DELETE\"}"));
    }

    @Test
    void testIfElseBlock() {
        String source = """
                {{ if visible }}<p>yes</p>{{ else }}<p>no</p>{{ end }}
                """;

        String java = compiler.compile(source, "test.page.html", "pages", "Test__Page");

        assertTrue(java.contains("if ("));
        assertTrue(java.contains("out.append(\"<p>yes</p>\");"));
        assertTrue(java.contains("} else {"));
        assertTrue(java.contains("out.append(\"<p>no</p>\");"));
    }

    @Test
    void testElseIfBlock() {
        String source = """
                {{ if a }}1{{ else if b }}2{{ else }}3{{ end }}
                """;

        String java = compiler.compile(source, "test.page.html", "pages", "Test__Page");

        // Should generate nested if/else
        assertTrue(java.contains("} else {"));
    }

    @Test
    void testForLoop() {
        String source = """
                <ul>{{ for item in items }}<li>{{ item }}</li>{{ end }}</ul>
                """;

        String java = compiler.compile(source, "test.page.html", "pages", "Test__Page");

        assertTrue(java.contains("for (var item : items)"));
        assertTrue(java.contains("out.appendEscaped(String.valueOf(item));"));
    }

    @Test
    void testPropertyAccess() {
        String source = """
                @inject PostService posts

                @init {
                  post = posts.getFirst();
                }

                {{ post.title }}
                """;

        String java = compiler.compile(source, "test.page.html", "pages", "Test__Page");

        assertTrue(java.contains("this.post.getTitle()"));
    }

    @Test
    void testNullSafePropertyAccess() {
        String source = """
                @inject PostService posts

                @init {
                  post = posts.getFirst();
                }

                {{ post?.title }}
                """;

        String java = compiler.compile(source, "test.page.html", "pages", "Test__Page");

        assertTrue(java.contains("this.post == null ? null : this.post.getTitle()"));
    }

    @Test
    void testRawExpression() {
        String source = """
                @inject PostService posts

                @init {
                  post = posts.getFirst();
                }

                {{ raw post.htmlContent }}
                """;

        String java = compiler.compile(source, "test.page.html", "pages", "Test__Page");

        assertTrue(java.contains("out.append(String.valueOf("));
        assertFalse(java.contains("out.appendEscaped") && java.contains("htmlContent"));
    }

    @Test
    void testEqualityUsesObjectsEquals() {
        String source = """
                {{ if status == "active" }}<span>active</span>{{ end }}
                """;

        String java = compiler.compile(source, "test.page.html", "pages", "Test__Page");

        assertTrue(java.contains("Objects.equals("));
    }

    @Test
    void testFragmentDefinitionAndCall() {
        String source = """
                @inject PostService posts

                @init {
                  post = posts.getFirst();
                }

                @fragment "post-content" {
                  <article>{{ post.content }}</article>
                }

                <div>{{ fragment "post-content" }}</div>
                """;

        String java = compiler.compile(source, "test.page.html", "pages", "Test__Page");

        assertTrue(java.contains("private void renderFragment_postContent(HtmlOutput out)"));
        assertTrue(java.contains("renderFragment_postContent(out);"));
        assertTrue(java.contains("renderFragment(String name, HtmlOutput out)"));
        assertTrue(java.contains("FragmentNotFoundException"));
    }

    @Test
    void testDeriveClassName() {
        assertEquals("Index__Page", CandiCompiler.deriveClassName("index.page.html"));
        assertEquals("PostEdit__Page", CandiCompiler.deriveClassName("post-edit.page.html"));
        assertEquals("UserProfile__Page", CandiCompiler.deriveClassName("user-profile.page.html"));
        assertEquals("Dashboard__Page", CandiCompiler.deriveClassName("dashboard.page.html"));
    }

    @Test
    void testFullPage() {
        String source = """
                @page "/post/{id}/edit"

                @inject PostService posts
                @inject RequestContext ctx
                @inject Auth auth

                @init {
                  post = posts.getById(ctx.path("id"));
                  canEdit = auth.isAdmin();
                }

                @action POST {
                  posts.update(ctx.path("id"), ctx.form("title"));
                  redirect("/posts");
                }

                @action DELETE {
                  posts.delete(ctx.path("id"));
                  redirect("/posts");
                }

                @fragment "post-content" {
                  <article>{{ post.content }}</article>
                }

                <!DOCTYPE html>
                <html>
                  <body>
                    <h1>{{ post.title }}</h1>
                    {{ if post.published }}
                      {{ fragment "post-content" }}
                    {{ end }}
                    {{ if canEdit }}
                      <form method="POST">
                        <input name="title" value="{{ post.title }}">
                        <button>Save</button>
                      </form>
                    {{ end }}
                  </body>
                </html>
                """;

        String java = compiler.compile(source, "post-edit.page.html", "pages", "PostEdit__Page");

        // Verify key parts of the generated code
        assertTrue(java.contains("class PostEdit__Page implements CandiPage"));
        assertTrue(java.contains("@CandiRoute(path = \"/post/{id}/edit\""));
        assertTrue(java.contains("private PostService posts;"));
        assertTrue(java.contains("private RequestContext ctx;"));
        assertTrue(java.contains("private Auth auth;"));
        assertTrue(java.contains("public void init()"));
        assertTrue(java.contains("handleAction(String method)"));
        assertTrue(java.contains("\"POST\".equals(method)"));
        assertTrue(java.contains("\"DELETE\".equals(method)"));
        assertTrue(java.contains("renderFragment_postContent"));
        assertTrue(java.contains("post.getTitle()"));
        assertTrue(java.contains("post.getPublished()"));
        assertTrue(java.contains("post.getContent()"));

        // Print for visual inspection
        System.out.println("=== Generated Java ===");
        System.out.println(java);
    }

    @Test
    void testBooleanExpressionInIf() {
        String source = """
                {{ if a && b }}<p>both</p>{{ end }}
                """;

        String java = compiler.compile(source, "test.page.html", "pages", "Test__Page");

        // Binary op should be used directly, not wrapped in truthiness check
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

        String java = compiler.compile(source, "test.page.html", "pages", "Test__Page");

        assertTrue(java.contains("for (var group : groups)"));
        assertTrue(java.contains("for (var item : group.getItems())"));
    }

    @Test
    void testHeaderOnlyPage() {
        String source = """
                @page "/api/logout"

                @action POST {
                  redirect("/login");
                }
                """;

        String java = compiler.compile(source, "logout.page.html", "pages", "Logout__Page");

        assertTrue(java.contains("class Logout__Page"));
        assertTrue(java.contains("handleAction"));
    }
}
