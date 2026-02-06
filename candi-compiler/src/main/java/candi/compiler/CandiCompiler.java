package candi.compiler;

import candi.compiler.ast.BodyNode;
import candi.compiler.ast.PageNode;
import candi.compiler.codegen.CodeGenerator;
import candi.compiler.lexer.Lexer;
import candi.compiler.lexer.Token;
import candi.compiler.parser.Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Main entry point for the Candi compiler (v2).
 * Compiles .page.html files (Java class + template) to Java source code.
 *
 * Pipeline:
 *   Source → Lexer (splits Java/template) → JavaAnalyzer + Parser → PageNode → CodeGenerator
 */
public class CandiCompiler {

    /**
     * Compile a .page.html source string to Java source code.
     *
     * @param source      the .page.html content (Java class + template)
     * @param fileName    the source file name (for error reporting)
     * @param packageName the Java package for the generated class
     * @param className   the Java class name for the generated class
     * @return generated Java source code
     */
    public String compile(String source, String fileName, String packageName, String className) {
        // Stage 1: Lex — split into Java source and template tokens
        Lexer lexer = new Lexer(source, fileName);
        List<Token> tokens = lexer.tokenize();
        String javaSource = lexer.getJavaSource();

        // Stage 2: Analyze Java source
        JavaAnalyzer analyzer = new JavaAnalyzer();
        JavaAnalyzer.ClassInfo classInfo;
        if (javaSource != null && !javaSource.isEmpty()) {
            classInfo = analyzer.analyze(javaSource);
        } else {
            classInfo = new JavaAnalyzer.ClassInfo(
                    className, null, null,
                    java.util.Set.of(), java.util.Map.of(),
                    java.util.Set.of(), java.util.Set.of());
        }

        // Stage 3: Parse template
        Parser parser = new Parser(tokens, fileName);
        BodyNode body = parser.parseBody();

        // Stage 4: Build AST
        String resolvedClassName = classInfo.className() != null ? classInfo.className() : className;
        PageNode ast = new PageNode(
                javaSource,
                resolvedClassName,
                classInfo.pagePath(),
                classInfo.layoutName(),
                classInfo.fieldNames(),
                classInfo.fieldTypes(),
                body,
                new SourceLocation(fileName, 1, 1)
        );

        // Stage 5: Generate
        CodeGenerator generator = new CodeGenerator(ast, packageName, resolvedClassName);
        return generator.generate();
    }

    /**
     * Compile a .page.html file to Java source code.
     * Class name is derived from file name. Package defaults to "pages".
     */
    public String compileFile(Path sourceFile) throws IOException {
        return compileFile(sourceFile, "pages");
    }

    /**
     * Compile a .page.html file to Java source code.
     */
    public String compileFile(Path sourceFile, String packageName) throws IOException {
        String source = Files.readString(sourceFile);
        String fileName = sourceFile.getFileName().toString();
        String className = deriveClassName(fileName);
        return compile(source, fileName, packageName, className);
    }

    /**
     * Derive a Java class name from a .page.html filename.
     * Examples:
     *   "index.page.html" → "Index__Page"
     *   "post-edit.page.html" → "PostEdit__Page"
     *   "user/profile.page.html" → "Profile__Page"
     */
    public static String deriveClassName(String fileName) {
        // Remove .page.html suffix
        String name = fileName;
        if (name.endsWith(".page.html")) {
            name = name.substring(0, name.length() - ".page.html".length());
        }
        // Remove path components
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        // Convert kebab-case to PascalCase
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : name.toCharArray()) {
            if (c == '-' || c == '_') {
                nextUpper = true;
            } else {
                sb.append(nextUpper ? Character.toUpperCase(c) : c);
                nextUpper = false;
            }
        }
        return sb + "__Page";
    }
}
