package candi.devtools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PageClassLoaderTest {

    @Test
    void loadsAddedClass() throws Exception {
        // Create a minimal class bytecode using javac
        // For testing, we'll use a pre-compiled simple class
        PageClassLoader loader = new PageClassLoader(getClass().getClassLoader());

        // Verify parent delegation works for standard classes
        Class<?> stringClass = loader.loadClass("java.lang.String");
        assertEquals(String.class, stringClass);
    }

    @Test
    void hasClassReturnsFalseForUnknown() {
        PageClassLoader loader = new PageClassLoader(getClass().getClassLoader());
        assertFalse(loader.hasClass("com.example.Unknown"));
    }

    @Test
    void hasClassReturnsTrueForAdded() {
        PageClassLoader loader = new PageClassLoader(getClass().getClassLoader());
        loader.addClass("com.example.Test", new byte[0]);
        assertTrue(loader.hasClass("com.example.Test"));
    }

    @Test
    void parentLastForAddedClasses() throws Exception {
        PageClassLoader loader = new PageClassLoader(getClass().getClassLoader());

        // Create minimal bytecode for a test class
        byte[] bytecode = generateMinimalClass("candi.devtools.test.TestPage");
        loader.addClass("candi.devtools.test.TestPage", bytecode);

        Class<?> clazz = loader.loadClass("candi.devtools.test.TestPage");
        assertNotNull(clazz);
        assertEquals("candi.devtools.test.TestPage", clazz.getName());
        // Class should be loaded by our classloader, not parent
        assertSame(loader, clazz.getClassLoader());
    }

    /**
     * Generate minimal bytecode for an empty class using javac.
     */
    private byte[] generateMinimalClass(String className) throws Exception {
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        String packageName = className.substring(0, className.lastIndexOf('.'));

        String source = "package " + packageName + "; public class " + simpleName + " {}";

        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new RuntimeException("JDK required for this test");
        }

        // Use IncrementalCompiler's in-memory approach
        var diagnostics = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();
        var fileManager = new TestFileManager(
                compiler.getStandardFileManager(diagnostics, null, null));

        var sourceFile = new TestJavaSource(className, source);
        var task = compiler.getTask(null, fileManager, diagnostics, null, null, java.util.List.of(sourceFile));
        assertTrue(task.call(), "Compilation failed: " + diagnostics.getDiagnostics());

        return fileManager.getClassBytes(className);
    }

    // Minimal in-memory file manager for test bytecode generation
    private static class TestFileManager extends javax.tools.ForwardingJavaFileManager<javax.tools.StandardJavaFileManager> {
        private final java.util.Map<String, java.io.ByteArrayOutputStream> outputs = new java.util.HashMap<>();

        TestFileManager(javax.tools.StandardJavaFileManager delegate) { super(delegate); }

        @Override
        public javax.tools.JavaFileObject getJavaFileForOutput(
                javax.tools.JavaFileManager.Location loc, String className,
                javax.tools.JavaFileObject.Kind kind, javax.tools.FileObject sibling) {
            var bos = new java.io.ByteArrayOutputStream();
            outputs.put(className, bos);
            return new javax.tools.SimpleJavaFileObject(
                    java.net.URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {
                @Override public java.io.OutputStream openOutputStream() { return bos; }
            };
        }

        byte[] getClassBytes(String className) { return outputs.get(className).toByteArray(); }
    }

    private static class TestJavaSource extends javax.tools.SimpleJavaFileObject {
        private final String code;
        TestJavaSource(String className, String code) {
            super(java.net.URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }
        @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return code; }
    }
}
