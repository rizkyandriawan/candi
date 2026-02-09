package candi.processor;

import candi.compiler.JavaAnalyzer;
import candi.compiler.ast.BodyNode;
import candi.compiler.codegen.SubclassCodeGenerator;
import candi.compiler.codegen.SubclassCodeGenerator.SubclassInput;
import candi.compiler.lexer.Lexer;
import candi.compiler.lexer.Token;
import candi.compiler.parser.Parser;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * JSR 269 annotation processor for Candi.
 *
 * <p>Processes classes annotated with {@code @Page}, {@code @Layout}, or {@code @Widget}
 * that also have a {@code @Template} annotation. Generates a {@code _Candi} subclass
 * with the appropriate Spring annotations and render() method.
 *
 * <p>Works with any build tool that runs javac (Maven, Gradle, Bazel) — no plugin needed.
 */
@SupportedAnnotationTypes({
        "candi.runtime.Page",
        "candi.runtime.Layout",
        "candi.runtime.Widget"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class CandiAnnotationProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element.getKind() != ElementKind.CLASS) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "Candi annotations can only be applied to classes", element);
                    continue;
                }
                processClass((TypeElement) element);
            }
        }
        return true;
    }

    private void processClass(TypeElement classElement) {
        // Extract @Template value
        String templateContent = extractTemplateContent(classElement);
        if (templateContent == null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Missing @Template annotation. Classes with @Page, @Layout, or @Widget must also have @Template.",
                    classElement);
            return;
        }

        // Validate: user class should NOT have @Component
        if (hasAnnotation(classElement, "org.springframework.stereotype.Component")) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Candi classes must not have @Component — the generated _Candi subclass provides it.",
                    classElement);
            return;
        }

        // Detect file type
        JavaAnalyzer.FileType fileType = detectFileType(classElement);

        // Extract metadata
        String className = classElement.getSimpleName().toString();
        String packageName = getPackageName(classElement);
        String pagePath = extractPagePath(classElement);
        String layoutName = extractLayoutName(classElement, fileType, className);

        // Extract fields
        Set<String> fieldNames = new LinkedHashSet<>();
        Map<String, String> fieldTypes = new LinkedHashMap<>();
        for (Element enclosed : classElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                VariableElement field = (VariableElement) enclosed;
                // Skip static fields
                if (field.getModifiers().contains(Modifier.STATIC)) continue;

                String fieldName = field.getSimpleName().toString();
                String fieldType = field.asType().toString();

                fieldNames.add(fieldName);
                fieldTypes.put(fieldName, simplifyType(fieldType));

                // Warn if private field has no getter (unless @Autowired)
                if (field.getModifiers().contains(Modifier.PRIVATE)
                        && !hasAnnotation(field, "org.springframework.beans.factory.annotation.Autowired")) {
                    String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                    if (!hasMethod(classElement, getterName)) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                                "Private field '" + fieldName + "' has no getter '" + getterName + "()'. " +
                                "Add @Getter (Lombok) or a manual getter for the generated _Candi class to access it.",
                                field);
                    }
                }
            }
        }

        // Extract action methods
        Set<String> actionMethods = new LinkedHashSet<>();
        for (Element enclosed : classElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) enclosed;
                if (hasAnnotation(method, "candi.runtime.Post")) actionMethods.add("POST");
                if (hasAnnotation(method, "candi.runtime.Put")) actionMethods.add("PUT");
                if (hasAnnotation(method, "candi.runtime.Delete")) actionMethods.add("DELETE");
                if (hasAnnotation(method, "candi.runtime.Patch")) actionMethods.add("PATCH");
            }
        }

        // Parse template
        String fileName = className + ".java";
        List<Token> tokens = Lexer.tokenizeTemplate(templateContent, fileName);
        Parser parser = new Parser(tokens, fileName);
        BodyNode body = parser.parseBody();

        // Generate
        SubclassInput input = new SubclassInput(
                className, packageName, fileType,
                pagePath, layoutName,
                fieldNames, fieldTypes, actionMethods,
                body);

        SubclassCodeGenerator generator = new SubclassCodeGenerator(input);
        String generatedSource = generator.generate();

        // Write output
        String generatedClassName = className + "_Candi";
        String qualifiedName = packageName.isEmpty() ? generatedClassName : packageName + "." + generatedClassName;

        try {
            JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(qualifiedName, classElement);
            try (Writer writer = sourceFile.openWriter()) {
                writer.write(generatedSource);
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Failed to write generated source: " + e.getMessage(), classElement);
        }
    }

    private String extractTemplateContent(TypeElement element) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            String annotationName = mirror.getAnnotationType().toString();
            if ("candi.runtime.Template".equals(annotationName)) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                        : mirror.getElementValues().entrySet()) {
                    if ("value".equals(entry.getKey().getSimpleName().toString())) {
                        return (String) entry.getValue().getValue();
                    }
                }
            }
        }
        return null;
    }

    private JavaAnalyzer.FileType detectFileType(TypeElement element) {
        if (hasAnnotation(element, "candi.runtime.Widget")) {
            return JavaAnalyzer.FileType.WIDGET;
        }
        if (hasAnnotation(element, "candi.runtime.Layout")
                && !hasAnnotation(element, "candi.runtime.Page")) {
            return JavaAnalyzer.FileType.LAYOUT;
        }
        return JavaAnalyzer.FileType.PAGE;
    }

    private String extractPagePath(TypeElement element) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if ("candi.runtime.Page".equals(mirror.getAnnotationType().toString())) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                        : mirror.getElementValues().entrySet()) {
                    if ("value".equals(entry.getKey().getSimpleName().toString())) {
                        return (String) entry.getValue().getValue();
                    }
                }
            }
        }
        return null;
    }

    private String extractLayoutName(TypeElement element, JavaAnalyzer.FileType fileType, String className) {
        if (fileType == JavaAnalyzer.FileType.LAYOUT) {
            String name = className;
            if (name.endsWith("Layout")) {
                name = name.substring(0, name.length() - "Layout".length());
            }
            return name.substring(0, 1).toLowerCase() + name.substring(1);
        }

        if (fileType == JavaAnalyzer.FileType.PAGE) {
            for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
                if ("candi.runtime.Page".equals(mirror.getAnnotationType().toString())) {
                    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                            : mirror.getElementValues().entrySet()) {
                        if ("layout".equals(entry.getKey().getSimpleName().toString())) {
                            String layout = (String) entry.getValue().getValue();
                            if (!layout.isEmpty()) return layout;
                        }
                    }
                }
            }
        }
        return null;
    }

    private String getPackageName(TypeElement element) {
        Element enclosing = element.getEnclosingElement();
        if (enclosing instanceof PackageElement pkg) {
            return pkg.getQualifiedName().toString();
        }
        return "";
    }

    private boolean hasAnnotation(Element element, String qualifiedName) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (qualifiedName.equals(mirror.getAnnotationType().toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMethod(TypeElement classElement, String methodName) {
        for (Element enclosed : classElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD
                    && enclosed.getSimpleName().toString().equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Simplify fully qualified type names to simple names for setParams casting.
     * e.g. "java.lang.String" → "String", "java.util.List<java.lang.String>" → "List<String>"
     */
    private String simplifyType(String typeName) {
        // For common types, strip java.lang prefix
        return typeName
                .replaceAll("java\\.lang\\.", "")
                .replaceAll("java\\.util\\.", "");
    }
}
