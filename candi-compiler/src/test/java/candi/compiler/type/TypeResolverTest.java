package candi.compiler.type;

import candi.compiler.JavaAnalyzer;
import candi.compiler.SourceLocation;
import candi.compiler.ast.BodyNode;
import candi.compiler.ast.PageNode;
import candi.compiler.lexer.Lexer;
import candi.compiler.lexer.Token;
import candi.compiler.parser.Parser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TypeResolverTest {

    /**
     * Build a PageNode from v2 syntax (Java class + template).
     */
    private PageNode parse(String source) {
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();
        String javaSource = lexer.getJavaSource();

        JavaAnalyzer analyzer = new JavaAnalyzer();
        JavaAnalyzer.ClassInfo classInfo;
        if (javaSource != null && !javaSource.isEmpty()) {
            classInfo = analyzer.analyze(javaSource);
        } else {
            classInfo = new JavaAnalyzer.ClassInfo(
                    "Test__Page", null, null,
                    Set.of(), Map.of(),
                    Set.of(), Set.of());
        }

        Parser parser = new Parser(tokens, "test.page.html");
        BodyNode body = parser.parseBody();

        String resolvedClassName = classInfo.className() != null ? classInfo.className() : "Test__Page";
        return new PageNode(
                javaSource,
                resolvedClassName,
                classInfo.pagePath(),
                classInfo.layoutName(),
                classInfo.fieldNames(),
                classInfo.fieldTypes(),
                body,
                new SourceLocation("test.page.html", 1, 1)
        );
    }

    @Test
    void resolvesFieldTypes() {
        PageNode page = parse("""
                @Page("/test")
                public class TestPage {

                    private String name;
                }

                <template>
                <p>{{ name }}</p>
                </template>
                """);

        TypeResolver resolver = new TypeResolver();
        List<TypeCheckError> errors = resolver.resolve(page);

        assertTrue(errors.isEmpty(), "Expected no errors, got: " + errors);
        assertEquals(TypeInfo.STRING, resolver.getVariableType("name"));
    }

    @Test
    void reportsUnknownFieldType() {
        PageNode page = parse("""
                @Page("/test")
                public class TestPage {

                    private NonExistentType foo;
                }

                <template>
                <p>{{ foo }}</p>
                </template>
                """);

        TypeResolver resolver = new TypeResolver();
        List<TypeCheckError> errors = resolver.resolve(page);

        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).message().contains("Cannot resolve type"));
    }

    @Test
    void resolvesStringProperty() {
        PageNode page = parse("""
                @Page("/test")
                public class TestPage {

                    private String name;
                }

                <template>
                <p>{{ name.length }}</p>
                </template>
                """);

        TypeResolver resolver = new TypeResolver();
        List<TypeCheckError> errors = resolver.resolve(page);

        // String doesn't have getLength(), but has length() method
        // The property resolver tries getLength() first, then isLength(), then length()
        assertTrue(errors.isEmpty(), "Expected no errors, got: " + errors);
    }

    @Test
    void resolvesMethodCall() {
        PageNode page = parse("""
                @Page("/test")
                public class TestPage {

                    private String name;
                }

                <template>
                <p>{{ name.toUpperCase() }}</p>
                </template>
                """);

        TypeResolver resolver = new TypeResolver();
        List<TypeCheckError> errors = resolver.resolve(page);

        assertTrue(errors.isEmpty(), "Expected no errors, got: " + errors);
    }

    @Test
    void reportsUnknownVariable() {
        PageNode page = parse("""
                @Page("/test")
                public class TestPage {
                }

                <template>
                <p>{{ nonexistent }}</p>
                </template>
                """);

        TypeResolver resolver = new TypeResolver();
        List<TypeCheckError> errors = resolver.resolve(page);

        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).message().contains("Unknown variable"));
    }

    @Test
    void handlesForLoopWithIterableType() {
        PageNode page = parse("""
                @Page("/test")
                public class TestPage {

                    private List items;
                }

                <template>
                {{ for item in items }}
                  <p>{{ item }}</p>
                {{ end }}
                </template>
                """);

        TypeResolver resolver = new TypeResolver();
        List<TypeCheckError> errors = resolver.resolve(page);

        assertTrue(errors.isEmpty(), "Expected no errors, got: " + errors);
        assertNotNull(resolver.getVariableType("item"));
    }

    @Test
    void handlesBooleanExpressions() {
        PageNode page = parse("""
                @Page("/test")
                public class TestPage {

                    private String name;
                }

                <template>
                {{ if name == "hello" }}
                  <p>Hi</p>
                {{ end }}
                </template>
                """);

        TypeResolver resolver = new TypeResolver();
        List<TypeCheckError> errors = resolver.resolve(page);

        assertTrue(errors.isEmpty(), "Expected no errors, got: " + errors);
    }

    @Test
    void handlesNullSafeAccess() {
        PageNode page = parse("""
                @Page("/test")
                public class TestPage {

                    private String name;
                }

                <template>
                <p>{{ name?.length }}</p>
                </template>
                """);

        TypeResolver resolver = new TypeResolver();
        List<TypeCheckError> errors = resolver.resolve(page);

        assertTrue(errors.isEmpty(), "Expected no errors, got: " + errors);
    }

    @Test
    void handlesAutowiredFields() {
        PageNode page = parse("""
                @Page("/test")
                public class TestPage {

                    @Autowired
                    private String greeting;
                }

                <template>
                <p>{{ greeting }}</p>
                </template>
                """);

        TypeResolver resolver = new TypeResolver();
        List<TypeCheckError> errors = resolver.resolve(page);

        assertTrue(errors.isEmpty(), "Expected no errors, got: " + errors);
        assertNotNull(resolver.getVariableType("greeting"));
        assertEquals(TypeInfo.STRING, resolver.getVariableType("greeting"));
    }
}
