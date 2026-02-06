package candi.devtools;

import candi.compiler.CandiCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;

/**
 * Compiles a single .page.html file to bytecode in-memory.
 * Pipeline: .page.html → Java source (CandiCompiler) → bytecode (javac).
 */
public class IncrementalCompiler {

    private static final Logger log = LoggerFactory.getLogger(IncrementalCompiler.class);
    private final CandiCompiler candiCompiler = new CandiCompiler();

    /**
     * Result of an incremental compilation.
     */
    public record CompileResult(
            String className,
            String fqcn,
            byte[] bytecode,
            List<String> errors
    ) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    /**
     * Compile a .page.html file to bytecode.
     *
     * @param pageFile    the .page.html file path
     * @param packageName the target package name
     * @param classpath   classpath entries for javac (JARs + class directories)
     * @return compilation result with bytecode or errors
     */
    public CompileResult compile(Path pageFile, String packageName, String classpath) {
        String className = CandiCompiler.deriveClassName(pageFile.getFileName().toString());
        String fqcn = packageName + "." + className;

        // Stage 1: Compile .page.html → Java source
        String javaSource;
        try {
            javaSource = candiCompiler.compileFile(pageFile, packageName);
        } catch (Exception e) {
            log.error("Candi compilation failed: {}", pageFile, e);
            return new CompileResult(className, fqcn, null,
                    List.of("Candi compilation error: " + e.getMessage()));
        }

        log.debug("Generated Java source for {}", className);

        // Stage 2: Compile Java → bytecode (in-memory)
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new CompileResult(className, fqcn, null,
                    List.of("JDK required: javax.tools.JavaCompiler not available. Run with JDK, not JRE."));
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        InMemoryFileManager fileManager = new InMemoryFileManager(
                compiler.getStandardFileManager(diagnostics, null, null));

        JavaFileObject sourceFile = new InMemoryJavaSource(fqcn, javaSource);

        List<String> options = new ArrayList<>();
        if (classpath != null && !classpath.isEmpty()) {
            options.add("-classpath");
            options.add(classpath);
        }

        JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics, options, null, List.of(sourceFile));

        boolean success = task.call();

        if (!success) {
            List<String> errors = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                if (d.getKind() == Diagnostic.Kind.ERROR) {
                    errors.add(String.format("Line %d: %s", d.getLineNumber(), d.getMessage(null)));
                }
            }
            log.error("Java compilation failed for {}: {}", className, errors);
            return new CompileResult(className, fqcn, null, errors);
        }

        byte[] bytecode = fileManager.getClassBytes(fqcn);
        if (bytecode == null) {
            return new CompileResult(className, fqcn, null,
                    List.of("Internal error: bytecode not captured for " + fqcn));
        }

        log.info("Compiled: {} ({} bytes)", fqcn, bytecode.length);
        return new CompileResult(className, fqcn, bytecode, List.of());
    }

    /**
     * In-memory Java source file for javac.
     */
    private static class InMemoryJavaSource extends SimpleJavaFileObject {
        private final String code;

        InMemoryJavaSource(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension),
                    Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    /**
     * In-memory class output file that captures bytecode.
     */
    private static class InMemoryClassOutput extends SimpleJavaFileObject {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        InMemoryClassOutput(String className) {
            super(URI.create("mem:///" + className.replace('.', '/') + Kind.CLASS.extension),
                    Kind.CLASS);
        }

        @Override
        public OutputStream openOutputStream() {
            return outputStream;
        }

        byte[] getBytes() {
            return outputStream.toByteArray();
        }
    }

    /**
     * File manager that captures compiled bytecode in memory.
     */
    private static class InMemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, InMemoryClassOutput> classOutputs = new HashMap<>();

        InMemoryFileManager(StandardJavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                    JavaFileObject.Kind kind, FileObject sibling) {
            InMemoryClassOutput output = new InMemoryClassOutput(className);
            classOutputs.put(className, output);
            return output;
        }

        byte[] getClassBytes(String className) {
            InMemoryClassOutput output = classOutputs.get(className);
            return output != null ? output.getBytes() : null;
        }
    }
}
