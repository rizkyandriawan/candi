package candi.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CandiAnnotationProcessor using javac directly.
 */
class CandiAnnotationProcessorTest {

    @TempDir
    Path tempDir;

    private boolean compileSource(String className, String source) throws IOException {
        return compileSource(className, source, null);
    }

    /**
     * Compile a Java source file with the annotation processor.
     * Returns true if compilation succeeded.
     */
    private boolean compileSource(String className, String source, List<Diagnostic<? extends JavaFileObject>> diagnosticsList) throws IOException {
        // Write source file
        Path srcDir = tempDir.resolve("src");
        Path pkgDir = srcDir.resolve("test");
        Files.createDirectories(pkgDir);
        Path srcFile = pkgDir.resolve(className + ".java");
        Files.writeString(srcFile, source);

        // Compile with annotation processor
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            Path outputDir = tempDir.resolve("out");
            Files.createDirectories(outputDir);
            Path generatedDir = tempDir.resolve("generated");
            Files.createDirectories(generatedDir);

            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(srcFile.toFile());

            // Get classpath for candi-runtime annotations
            String classpath = System.getProperty("java.class.path");

            List<String> options = List.of(
                    "-d", outputDir.toString(),
                    "-s", generatedDir.toString(),
                    "-classpath", classpath,
                    "-processor", "candi.processor.CandiAnnotationProcessor"
            );

            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, fileManager, diagnostics, options, null, compilationUnits);

            boolean success = task.call();

            if (diagnosticsList != null) {
                diagnosticsList.addAll(diagnostics.getDiagnostics());
            }

            if (!success) {
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    System.err.println(d.getKind() + ": " + d.getMessage(null));
                }
            }

            return success;
        }
    }

    private String readGenerated(String className) throws IOException {
        Path generatedDir = tempDir.resolve("generated");
        Path generated = generatedDir.resolve("test").resolve(className + "_Candi.java");
        if (Files.exists(generated)) {
            return Files.readString(generated);
        }
        // Also check root
        generated = generatedDir.resolve(className + "_Candi.java");
        if (Files.exists(generated)) {
            return Files.readString(generated);
        }
        fail("Generated file not found: " + className + "_Candi.java");
        return null;
    }

    @Test
    void testSimplePage() throws IOException {
        String source = """
                package test;

                import candi.runtime.Page;
                import candi.runtime.Template;

                @Page("/hello")
                @Template(\"\"\"
                <h1>Hello World</h1>
                \"\"\")
                public class HelloPage {
                }
                """;

        assertTrue(compileSource("HelloPage", source));

        String generated = readGenerated("HelloPage");
        assertTrue(generated.contains("class HelloPage_Candi extends HelloPage"));
        assertTrue(generated.contains("implements CandiPage"));
        assertTrue(generated.contains("@CandiRoute(path = \"/hello\""));
        assertTrue(generated.contains("out.append(\"<h1>Hello World</h1>"));
    }

    @Test
    void testPageWithFieldsUsesGetters() throws IOException {
        String source = """
                package test;

                import candi.runtime.Page;
                import candi.runtime.Template;

                @Page("/greet")
                @Template(\"\"\"
                <h1>{{ name }}</h1>
                \"\"\")
                public class GreetPage {
                    private String name;

                    public String getName() { return name; }
                }
                """;

        assertTrue(compileSource("GreetPage", source));

        String generated = readGenerated("GreetPage");
        assertTrue(generated.contains("this.getName()"));
        assertFalse(generated.contains("this.name"));
    }

    @Test
    void testPageWithLayout() throws IOException {
        String source = """
                package test;

                import candi.runtime.Page;
                import candi.runtime.Template;

                @Page(value = "/about", layout = "base")
                @Template(\"\"\"
                <h1>About</h1>
                \"\"\")
                public class AboutPage {
                }
                """;

        assertTrue(compileSource("AboutPage", source));

        String generated = readGenerated("AboutPage");
        assertTrue(generated.contains("private CandiLayout baseLayout;"));
        assertTrue(generated.contains("baseLayout.render(out,"));
    }

    @Test
    void testLayoutGeneration() throws IOException {
        String source = """
                package test;

                import candi.runtime.Layout;
                import candi.runtime.Template;

                @Layout
                @Template(\"\"\"
                <html><body>{{ content }}</body></html>
                \"\"\")
                public class BaseLayout {
                }
                """;

        assertTrue(compileSource("BaseLayout", source));

        String generated = readGenerated("BaseLayout");
        assertTrue(generated.contains("class BaseLayout_Candi extends BaseLayout implements CandiLayout"));
        assertTrue(generated.contains("@Component(\"baseLayout\")"));
        assertTrue(generated.contains("slots.renderSlot(\"content\", out)"));
    }

    @Test
    void testWidgetGeneration() throws IOException {
        String source = """
                package test;

                import candi.runtime.Widget;
                import candi.runtime.Template;

                @Widget
                @Template(\"\"\"
                <div class="alert">{{ message }}</div>
                \"\"\")
                public class AlertWidget {
                    private String message;

                    public String getMessage() { return message; }
                    public void setMessage(String message) { this.message = message; }
                }
                """;

        assertTrue(compileSource("AlertWidget", source));

        String generated = readGenerated("AlertWidget");
        assertTrue(generated.contains("class AlertWidget_Candi extends AlertWidget implements CandiComponent"));
        assertTrue(generated.contains("@Component(\"AlertWidget__Widget\")"));
        assertTrue(generated.contains("@Scope(\"prototype\")"));
        assertTrue(generated.contains("this.setMessage("));
        assertTrue(generated.contains("this.getMessage()"));
    }

    @Test
    void testPageWithActions() throws IOException {
        String source = """
                package test;

                import candi.runtime.Page;
                import candi.runtime.Post;
                import candi.runtime.Delete;
                import candi.runtime.Template;
                import candi.runtime.ActionResult;

                @Page("/item")
                @Template(\"\"\"
                <div>item</div>
                \"\"\")
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
                """;

        assertTrue(compileSource("ItemPage", source));

        String generated = readGenerated("ItemPage");
        assertTrue(generated.contains("methods = {\"GET\", \"POST\", \"DELETE\"}"));
    }

    @Test
    void testPageWithFragment() throws IOException {
        String source = """
                package test;

                import candi.runtime.Page;
                import candi.runtime.Template;

                @Page("/posts")
                @Template(\"""
                <h1>Posts</h1>
                {{ fragment "post-list" }}
                <ul><li>items</li></ul>
                {{ end }}
                \""")
                public class PostsPage {
                }
                """;

        assertTrue(compileSource("PostsPage", source));

        String generated = readGenerated("PostsPage");
        assertTrue(generated.contains("class PostsPage_Candi extends PostsPage"));
        assertTrue(generated.contains("implements CandiPage"));
        // Fragment dispatch method
        assertTrue(generated.contains("public void renderFragment(String _name, HtmlOutput out)"));
        assertTrue(generated.contains("case \"post-list\""));
        assertTrue(generated.contains("renderFragment_post_list(out)"));
        // Per-fragment method
        assertTrue(generated.contains("private void renderFragment_post_list(HtmlOutput out)"));
    }

    @Test
    void testMissingTemplateProducesError() throws IOException {
        String source = """
                package test;

                import candi.runtime.Page;

                @Page("/broken")
                public class BrokenPage {
                }
                """;

        var diagnostics = new java.util.ArrayList<Diagnostic<? extends JavaFileObject>>();
        assertFalse(compileSource("BrokenPage", source, diagnostics));

        boolean hasTemplateError = diagnostics.stream()
                .anyMatch(d -> d.getMessage(null).contains("@Template"));
        assertTrue(hasTemplateError, "Should report missing @Template error");
    }
}
