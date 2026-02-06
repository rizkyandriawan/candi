package candi.devtools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class IncrementalCompilerTest {

    @TempDir
    Path tempDir;

    @Test
    void compilesSimplePageToBytecode() throws Exception {
        // Create a simple .page.html file
        Path pageFile = tempDir.resolve("hello.page.html");
        Files.writeString(pageFile, """
                @page "/hello"

                <h1>Hello World</h1>
                """);

        IncrementalCompiler compiler = new IncrementalCompiler();
        String classpath = System.getProperty("java.class.path");

        IncrementalCompiler.CompileResult result =
                compiler.compile(pageFile, "pages", classpath);

        assertFalse(result.hasErrors(), "Errors: " + result.errors());
        assertEquals("Hello__Page", result.className());
        assertEquals("pages.Hello__Page", result.fqcn());
        assertNotNull(result.bytecode());
        assertTrue(result.bytecode().length > 0);
    }

    @Test
    void compilesPageWithExpressions() throws Exception {
        Path pageFile = tempDir.resolve("greeting.page.html");
        Files.writeString(pageFile, """
                @page "/greet"

                @init {
                  name = "World";
                }

                <h1>Hello {{ name }}</h1>
                """);

        IncrementalCompiler compiler = new IncrementalCompiler();
        String classpath = System.getProperty("java.class.path");

        IncrementalCompiler.CompileResult result =
                compiler.compile(pageFile, "pages", classpath);

        assertFalse(result.hasErrors(), "Errors: " + result.errors());
        assertNotNull(result.bytecode());
    }

    @Test
    void reportsCandiCompilationErrors() throws Exception {
        Path pageFile = tempDir.resolve("broken.page.html");
        Files.writeString(pageFile, """
                @page "/broken"
                @unknown_directive

                <h1>Broken</h1>
                """);

        IncrementalCompiler compiler = new IncrementalCompiler();
        String classpath = System.getProperty("java.class.path");

        IncrementalCompiler.CompileResult result =
                compiler.compile(pageFile, "pages", classpath);

        assertTrue(result.hasErrors());
        assertNull(result.bytecode());
    }

    @Test
    void loadCompiledClassViaPageClassLoader() throws Exception {
        Path pageFile = tempDir.resolve("test.page.html");
        Files.writeString(pageFile, """
                @page "/test"

                <p>Test page</p>
                """);

        IncrementalCompiler compiler = new IncrementalCompiler();
        String classpath = System.getProperty("java.class.path");

        IncrementalCompiler.CompileResult result =
                compiler.compile(pageFile, "pages", classpath);

        assertFalse(result.hasErrors(), "Errors: " + result.errors());

        // Load via PageClassLoader
        PageClassLoader loader = new PageClassLoader(getClass().getClassLoader());
        loader.addClass(result.fqcn(), result.bytecode());

        Class<?> pageClass = loader.loadClass(result.fqcn());
        assertNotNull(pageClass);
        assertEquals("pages.Test__Page", pageClass.getName());

        // Verify it implements CandiPage
        assertTrue(candi.runtime.CandiPage.class.isAssignableFrom(pageClass));

        // Verify it has @CandiRoute annotation
        candi.runtime.CandiRoute route = pageClass.getAnnotation(candi.runtime.CandiRoute.class);
        assertNotNull(route);
        assertEquals("/test", route.path());
    }

    @Test
    void compiledPageCanBeInstantiated() throws Exception {
        Path pageFile = tempDir.resolve("simple.page.html");
        Files.writeString(pageFile, """
                @page "/simple"

                <h1>Simple Page</h1>
                """);

        IncrementalCompiler compiler = new IncrementalCompiler();
        String classpath = System.getProperty("java.class.path");

        IncrementalCompiler.CompileResult result =
                compiler.compile(pageFile, "pages", classpath);

        assertFalse(result.hasErrors(), "Errors: " + result.errors());

        PageClassLoader loader = new PageClassLoader(getClass().getClassLoader());
        loader.addClass(result.fqcn(), result.bytecode());

        Class<?> pageClass = loader.loadClass(result.fqcn());
        Object instance = pageClass.getDeclaredConstructor().newInstance();

        // Render the page
        candi.runtime.HtmlOutput out = new candi.runtime.HtmlOutput();
        ((candi.runtime.CandiPage) instance).render(out);

        String html = out.toString();
        assertTrue(html.contains("<h1>Simple Page</h1>"), "Got: " + html);
    }
}
