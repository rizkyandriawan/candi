package candi.processor;

import candi.compiler.JavaAnalyzer;
import candi.compiler.ast.BodyNode;
import candi.compiler.codegen.SubclassCodeGenerator;
import candi.compiler.codegen.SubclassCodeGenerator.RequestParamInfo;
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
        Map<String, RequestParamInfo> requestParams = new LinkedHashMap<>();
        Map<String, String> pathVariables = new LinkedHashMap<>();
        Set<String> pageableFields = new LinkedHashSet<>();

        boolean classHasLombokSetter = hasAnnotation(classElement, "lombok.Setter")
                || hasAnnotation(classElement, "lombok.Data");

        for (Element enclosed : classElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.FIELD) {
                VariableElement field = (VariableElement) enclosed;
                // Skip static fields
                if (field.getModifiers().contains(Modifier.STATIC)) continue;

                String fieldName = field.getSimpleName().toString();
                String fieldType = field.asType().toString();

                fieldNames.add(fieldName);
                fieldTypes.put(fieldName, simplifyType(fieldType));

                boolean hasRequestParam = hasAnnotation(field, "candi.runtime.RequestParam");
                boolean hasPathVariable = hasAnnotation(field, "candi.runtime.PathVariable");
                boolean hasAutowired = hasAnnotation(field, "org.springframework.beans.factory.annotation.Autowired");
                boolean isPageable = fieldType.equals("org.springframework.data.domain.Pageable");

                // Validate: @Autowired conflicts with @RequestParam/@PathVariable
                if (hasAutowired && (hasRequestParam || hasPathVariable)) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "Field '" + fieldName + "' cannot have both @Autowired and @RequestParam/@PathVariable.",
                            field);
                    continue;
                }

                // Detect @RequestParam
                if (hasRequestParam) {
                    String paramName = extractAnnotationStringValue(field,
                            "candi.runtime.RequestParam", "value");
                    if (paramName == null || paramName.isEmpty()) {
                        paramName = extractAnnotationStringValue(field,
                                "candi.runtime.RequestParam", "name");
                    }
                    if (paramName == null || paramName.isEmpty()) {
                        paramName = fieldName;
                    }
                    String defaultValue = extractAnnotationStringValue(field,
                            "candi.runtime.RequestParam", "defaultValue");
                    // Sentinel "\u0000" means "not set"
                    if (defaultValue != null && defaultValue.equals("\u0000")) {
                        defaultValue = null;
                    }
                    boolean required = extractAnnotationBooleanValue(field,
                            "candi.runtime.RequestParam", "required", false);
                    requestParams.put(fieldName, new RequestParamInfo(paramName, defaultValue, required));
                    validateSetterAvailable(classElement, field, fieldName, classHasLombokSetter);
                }

                // Detect @PathVariable
                if (hasPathVariable) {
                    String varName = extractAnnotationStringValue(field,
                            "candi.runtime.PathVariable", "value");
                    if (varName == null || varName.isEmpty()) {
                        varName = extractAnnotationStringValue(field,
                                "candi.runtime.PathVariable", "name");
                    }
                    if (varName == null || varName.isEmpty()) {
                        varName = fieldName;
                    }
                    pathVariables.put(fieldName, varName);
                    validateSetterAvailable(classElement, field, fieldName, classHasLombokSetter);
                }

                // Detect Pageable type
                if (isPageable) {
                    pageableFields.add(fieldName);
                    validateSetterAvailable(classElement, field, fieldName, classHasLombokSetter);
                }

                // Warn if private field has no getter (unless @Autowired)
                if (field.getModifiers().contains(Modifier.PRIVATE)
                        && !hasAutowired && !hasRequestParam && !hasPathVariable && !isPageable) {
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

        // Extract action methods and detect init()
        Set<String> actionMethods = new LinkedHashSet<>();
        boolean hasInitMethod = false;
        for (Element enclosed : classElement.getEnclosedElements()) {
            if (enclosed.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) enclosed;
                if (hasAnnotation(method, "candi.runtime.Post")) actionMethods.add("POST");
                if (hasAnnotation(method, "candi.runtime.Put")) actionMethods.add("PUT");
                if (hasAnnotation(method, "candi.runtime.Delete")) actionMethods.add("DELETE");
                if (hasAnnotation(method, "candi.runtime.Patch")) actionMethods.add("PATCH");
                if ("init".equals(method.getSimpleName().toString())
                        && method.getParameters().isEmpty()) {
                    hasInitMethod = true;
                }
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
                body,
                requestParams, pathVariables, pageableFields, hasInitMethod);

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

    private void validateSetterAvailable(TypeElement classElement, VariableElement field,
                                         String fieldName, boolean classHasLombokSetter) {
        if (!field.getModifiers().contains(Modifier.PRIVATE)) return; // package-private works directly
        if (classHasLombokSetter || hasAnnotation(field, "lombok.Setter")) return;

        String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        if (!hasMethod(classElement, setterName)) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Private field '" + fieldName + "' with @RequestParam/@PathVariable/Pageable requires a setter '" +
                    setterName + "()'. Add @Setter (Lombok) or a manual setter.",
                    field);
        }
    }

    private String extractAnnotationStringValue(Element element, String annotationFqn, String attribute) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (annotationFqn.equals(mirror.getAnnotationType().toString())) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                        : mirror.getElementValues().entrySet()) {
                    if (attribute.equals(entry.getKey().getSimpleName().toString())) {
                        Object val = entry.getValue().getValue();
                        return val != null ? val.toString() : null;
                    }
                }
            }
        }
        return null;
    }

    private boolean extractAnnotationBooleanValue(Element element, String annotationFqn,
                                                   String attribute, boolean defaultValue) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (annotationFqn.equals(mirror.getAnnotationType().toString())) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                        : mirror.getElementValues().entrySet()) {
                    if (attribute.equals(entry.getKey().getSimpleName().toString())) {
                        Object val = entry.getValue().getValue();
                        if (val instanceof Boolean b) return b;
                    }
                }
            }
        }
        return defaultValue;
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
