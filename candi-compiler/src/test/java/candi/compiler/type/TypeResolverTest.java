package candi.compiler.type;

import candi.compiler.CandiCompiler;
import candi.compiler.ast.PageNode;
import candi.compiler.lexer.Lexer;
import candi.compiler.lexer.Token;
import candi.compiler.parser.Parser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TypeResolverTest {

    private PageNode parse(String source) {
        Lexer lexer = new Lexer(source, "test.page.html");
        List<Token> tokens = lexer.tokenize();
        Parser parser = new Parser(tokens, "test.page.html");
        return parser.parse();
    }

    @Test
    void resolvesInjectTypes() {
        PageNode page = parse("""
                @page "/test"
                @inject String name

                <p>{{ name }}</p>
                """);

        TypeResolver resolver = new TypeResolver();
        List<TypeCheckError> errors = resolver.resolve(page);

        assertTrue(errors.isEmpty(), "Expected no errors, got: " + errors);
        assertEquals(TypeInfo.STRING, resolver.getVariableType("name"));
    }

    @Test
    void reportsUnknownInjectType() {
        PageNode page = parse("""
                @page "/test"
                @inject NonExistentType foo

                <p>{{ foo }}</p>
                """);

        TypeResolver resolver = new TypeResolver();
        List<TypeCheckError> errors = resolver.resolve(page);

        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).message().contains("Cannot resolve type"));
    }

    @Test
    void resolvesStringProperty() {
        PageNode page = parse("""
                @page "/test"
                @inject String name

                <p>{{ name.length }}</p>
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
                @page "/test"
                @inject String name

                <p>{{ name.toUpperCase() }}</p>
                """);

        TypeResolver resolver = new TypeResolver();
        List<TypeCheckError> errors = resolver.resolve(page);

        assertTrue(errors.isEmpty(), "Expected no errors, got: " + errors);
    }

    @Test
    void reportsUnknownVariable() {
        PageNode page = parse("""
                @page "/test"

                <p>{{ nonexistent }}</p>
                """);

        TypeResolver resolver = new TypeResolver();
        List<TypeCheckError> errors = resolver.resolve(page);

        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).message().contains("Unknown variable"));
    }

    @Test
    void handlesForLoopWithIterableType() {
        PageNode page = parse("""
                @page "/test"
                @inject List items

                {{ for item in items }}
                  <p>{{ item }}</p>
                {{ end }}
                """);

        TypeResolver resolver = new TypeResolver();
        List<TypeCheckError> errors = resolver.resolve(page);

        assertTrue(errors.isEmpty(), "Expected no errors, got: " + errors);
        assertNotNull(resolver.getVariableType("item"));
    }

    @Test
    void handlesBooleanExpressions() {
        PageNode page = parse("""
                @page "/test"
                @inject String name

                {{ if name == "hello" }}
                  <p>Hi</p>
                {{ end }}
                """);

        TypeResolver resolver = new TypeResolver();
        List<TypeCheckError> errors = resolver.resolve(page);

        assertTrue(errors.isEmpty(), "Expected no errors, got: " + errors);
    }

    @Test
    void handlesNullSafeAccess() {
        PageNode page = parse("""
                @page "/test"
                @inject String name

                <p>{{ name?.length }}</p>
                """);

        TypeResolver resolver = new TypeResolver();
        List<TypeCheckError> errors = resolver.resolve(page);

        assertTrue(errors.isEmpty(), "Expected no errors, got: " + errors);
    }

    @Test
    void handlesInitVariables() {
        PageNode page = parse("""
                @page "/test"
                @inject String name

                @init {
                  greeting = name;
                }

                <p>{{ greeting }}</p>
                """);

        TypeResolver resolver = new TypeResolver();
        List<TypeCheckError> errors = resolver.resolve(page);

        assertTrue(errors.isEmpty(), "Expected no errors, got: " + errors);
        assertNotNull(resolver.getVariableType("greeting"));
    }
}
