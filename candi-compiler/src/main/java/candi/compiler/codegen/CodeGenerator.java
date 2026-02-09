package candi.compiler.codegen;

import candi.compiler.JavaAnalyzer;
import candi.compiler.ast.*;
import candi.compiler.expr.Expression;

import java.util.*;

/**
 * Generates Java source code from a .jhtml AST.
 * Branches by file type (PAGE, LAYOUT, WIDGET) to produce different Spring beans:
 *   PAGE   → implements CandiPage, @Component, @Scope(REQUEST), @CandiRoute
 *   LAYOUT → implements CandiLayout, @Component("nameLayout"), singleton
 *   WIDGET → implements CandiComponent, @Component("Name__Widget"), @Scope("prototype")
 */
public class CodeGenerator {

    private final PageNode page;
    private final String packageName;
    private final String className;
    private final StringBuilder sb = new StringBuilder();
    private int indent = 0;

    public CodeGenerator(PageNode page, String packageName, String className) {
        this.page = page;
        this.packageName = packageName;
        this.className = className;
    }

    public String generate() {
        generatePackage();
        generateImports();
        generateClassWithRender();
        return sb.toString();
    }

    private void generatePackage() {
        if (packageName != null && !packageName.isEmpty()) {
            line("package " + packageName + ";");
            line("");
        }
    }

    private void generateImports() {
        line("import org.springframework.beans.factory.annotation.Autowired;");
        line("import org.springframework.context.annotation.Scope;");
        line("import org.springframework.stereotype.Component;");
        line("import org.springframework.web.context.WebApplicationContext;");
        line("import candi.runtime.*;");
        line("import java.util.Objects;");
        if (hasComponentCalls()) {
            line("import org.springframework.context.ApplicationContext;");
            line("import java.util.Map;");
            line("import java.util.HashMap;");
        }

        // Add imports from user's Java source (extract import lines)
        if (page.javaSource() != null && !page.javaSource().isEmpty()) {
            for (String srcLine : page.javaSource().split("\n")) {
                String trimmed = srcLine.trim();
                if (trimmed.startsWith("import ")) {
                    line(trimmed);
                }
            }
        }
        line("");
    }

    /**
     * Generate the class by transforming the user's Java source.
     * Branches by file type for different annotations and interfaces.
     */
    private void generateClassWithRender() {
        String javaSource = page.javaSource();

        if (javaSource == null || javaSource.isEmpty()) {
            // Body-only file (include file) — generate minimal class
            generateMinimalClass();
            return;
        }

        JavaAnalyzer.FileType fileType = page.fileType();

        switch (fileType) {
            case PAGE -> generatePageClass(javaSource);
            case LAYOUT -> generateLayoutClass(javaSource);
            case WIDGET -> generateWidgetClass(javaSource);
        }
    }

    // ========== PAGE Generation ==========

    private void generatePageClass(String javaSource) {
        // Build methods set for @CandiRoute
        Set<String> methods = new LinkedHashSet<>();
        methods.add("GET");
        if (javaSource.contains("@Post")) methods.add("POST");
        if (javaSource.contains("@Put")) methods.add("PUT");
        if (javaSource.contains("@Delete")) methods.add("DELETE");
        if (javaSource.contains("@Patch")) methods.add("PATCH");

        String methodsStr = methods.stream()
                .map(m -> "\"" + m + "\"")
                .reduce((a, b) -> a + ", " + b)
                .orElse("\"GET\"");

        // Add annotations before class declaration
        line("@Component");
        line("@Scope(WebApplicationContext.SCOPE_REQUEST)");
        if (page.pagePath() != null) {
            line("@CandiRoute(path = \"" + escapeJavaString(page.pagePath()) + "\", methods = {" + methodsStr + "})");
        }

        // Transform class
        String[] lines = javaSource.split("\n");
        boolean classStarted = false;
        boolean needsApplicationContext = hasComponentCalls() &&
                !javaSource.contains("ApplicationContext");

        for (String srcLine : lines) {
            String trimmed = srcLine.trim();

            // Skip package/import statements
            if (trimmed.startsWith("package ") || trimmed.startsWith("import ")) continue;

            // Skip user annotations that we handle ourselves
            if (trimmed.startsWith("@Page(") || trimmed.startsWith("@Layout(") ||
                trimmed.startsWith("@Layout") || trimmed.startsWith("@Widget") ||
                trimmed.startsWith("@Template")) continue;

            // Transform class declaration
            if (!classStarted && trimmed.contains("class " + page.className())) {
                String modifiedLine = trimmed;
                if (!modifiedLine.contains("implements")) {
                    modifiedLine = modifiedLine.replace("{", "implements CandiPage {");
                } else if (!modifiedLine.contains("CandiPage")) {
                    modifiedLine = modifiedLine.replace("implements ", "implements CandiPage, ");
                }
                line(modifiedLine);
                classStarted = true;
                indent++;

                if (needsApplicationContext) {
                    line("");
                    line("@Autowired");
                    line("private ApplicationContext _applicationContext;");
                }

                // Inject layout field if page uses a layout
                if (page.layoutName() != null) {
                    line("");
                    line("@Autowired");
                    line("private CandiLayout " + layoutFieldName(page.layoutName()) + ";");
                }
                continue;
            }

            // Skip the closing brace of the class
            if (classStarted && trimmed.equals("}") && isLastClosingBrace(lines, srcLine)) continue;

            if (classStarted) {
                line(trimmed);
            } else {
                line(srcLine);
            }
        }

        // Generate render method
        line("");
        generatePageRender();

        indent--;
        line("}");
    }

    private void generatePageRender() {
        line("@Override");
        line("public void render(HtmlOutput out) {");
        indent++;
        if (page.layoutName() != null) {
            String layoutField = layoutFieldName(page.layoutName());
            line(layoutField + ".render(out, (slotName, slotOut) -> {");
            indent++;
            line("if (\"content\".equals(slotName)) {");
            indent++;
            if (page.body() != null) {
                generateBodyNodes(page.body().children());
            }
            indent--;
            line("}");
            indent--;
            line("});");
        } else if (page.body() != null) {
            generateBodyNodes(page.body().children());
        }
        indent--;
        line("}");
    }

    // ========== LAYOUT Generation ==========

    private void generateLayoutClass(String javaSource) {
        String layoutName = page.layoutName();
        line("@Component(\"" + escapeJavaString(layoutName) + "Layout\")");

        String[] lines = javaSource.split("\n");
        boolean classStarted = false;

        for (String srcLine : lines) {
            String trimmed = srcLine.trim();

            if (trimmed.startsWith("package ") || trimmed.startsWith("import ")) continue;
            if (trimmed.startsWith("@Layout") || trimmed.startsWith("@Template")) continue;

            if (!classStarted && trimmed.contains("class " + page.className())) {
                String modifiedLine = trimmed;
                if (!modifiedLine.contains("implements")) {
                    modifiedLine = modifiedLine.replace("{", "implements CandiLayout {");
                } else if (!modifiedLine.contains("CandiLayout")) {
                    modifiedLine = modifiedLine.replace("implements ", "implements CandiLayout, ");
                }
                line(modifiedLine);
                classStarted = true;
                indent++;
                continue;
            }

            if (classStarted && trimmed.equals("}") && isLastClosingBrace(lines, srcLine)) continue;

            if (classStarted) {
                line(trimmed);
            } else {
                line(srcLine);
            }
        }

        // Generate layout render method
        line("");
        generateLayoutRender();

        indent--;
        line("}");
    }

    private void generateLayoutRender() {
        line("@Override");
        line("public void render(HtmlOutput out, SlotProvider slots) {");
        indent++;
        if (page.body() != null) {
            generateBodyNodes(page.body().children());
        }
        indent--;
        line("}");
    }

    // ========== WIDGET Generation ==========

    private void generateWidgetClass(String javaSource) {
        String beanName = page.className() + "__Widget";
        line("@Component(\"" + escapeJavaString(beanName) + "\")");
        line("@Scope(\"prototype\")");

        String[] lines = javaSource.split("\n");
        boolean classStarted = false;

        for (String srcLine : lines) {
            String trimmed = srcLine.trim();

            if (trimmed.startsWith("package ") || trimmed.startsWith("import ")) continue;
            if (trimmed.startsWith("@Widget") || trimmed.startsWith("@Template")) continue;

            if (!classStarted && trimmed.contains("class " + page.className())) {
                String modifiedLine = trimmed;
                if (!modifiedLine.contains("implements")) {
                    modifiedLine = modifiedLine.replace("{", "implements CandiComponent {");
                } else if (!modifiedLine.contains("CandiComponent")) {
                    modifiedLine = modifiedLine.replace("implements ", "implements CandiComponent, ");
                }
                line(modifiedLine);
                classStarted = true;
                indent++;
                continue;
            }

            if (classStarted && trimmed.equals("}") && isLastClosingBrace(lines, srcLine)) continue;

            if (classStarted) {
                line(trimmed);
            } else {
                line(srcLine);
            }
        }

        // Generate setParams and render methods
        line("");
        generateWidgetSetParams();
        line("");
        generateWidgetRender();

        indent--;
        line("}");
    }

    private void generateWidgetSetParams() {
        line("@Override");
        line("public void setParams(java.util.Map<String, Object> params) {");
        indent++;
        // Set each field from params map
        for (String fieldName : page.fieldNames()) {
            String type = page.fieldTypes().get(fieldName);
            if (type != null) {
                line("if (params.containsKey(\"" + fieldName + "\")) {");
                indent++;
                line("this." + fieldName + " = (" + type + ") params.get(\"" + fieldName + "\");");
                indent--;
                line("}");
            }
        }
        indent--;
        line("}");
    }

    private void generateWidgetRender() {
        line("@Override");
        line("public void render(HtmlOutput out) {");
        indent++;
        if (page.body() != null) {
            generateBodyNodes(page.body().children());
        }
        indent--;
        line("}");
    }

    // ========== Minimal Class (body-only) ==========

    private void generateMinimalClass() {
        line("@Component");
        line("@Scope(WebApplicationContext.SCOPE_REQUEST)");
        line("public class " + className + " implements CandiPage {");
        indent++;
        if (hasComponentCalls()) {
            line("");
            line("@Autowired");
            line("private ApplicationContext _applicationContext;");
        }
        line("");
        generatePageRender();
        indent--;
        line("}");
    }

    // ========== Body Node Code Generation ==========

    private void generateBodyNodes(List<Node> nodes) {
        for (Node node : nodes) {
            generateBodyNode(node);
        }
    }

    private void generateBodyNode(Node node) {
        switch (node) {
            case HtmlNode html -> generateHtml(html);
            case ExpressionOutputNode expr -> generateExpressionOutput(expr);
            case RawExpressionOutputNode raw -> generateRawExpressionOutput(raw);
            case IfNode ifNode -> generateIf(ifNode);
            case ForNode forNode -> generateFor(forNode);
            case IncludeNode include -> generateInclude(include);
            case ComponentCallNode call -> generateWidgetCall(call);
            case ContentNode content -> generateContent(content);
            default -> throw new IllegalStateException("Unexpected node in body: " + node.getClass());
        }
    }

    private void generateHtml(HtmlNode html) {
        String content = html.content();
        if (!content.isEmpty()) {
            line("out.append(\"" + escapeJavaString(content) + "\");");
        }
    }

    private void generateExpressionOutput(ExpressionOutputNode node) {
        String javaExpr = generateExpression(node.expression());
        line("out.appendEscaped(String.valueOf(" + javaExpr + "));");
    }

    private void generateRawExpressionOutput(RawExpressionOutputNode node) {
        String javaExpr = generateExpression(node.expression());
        line("out.append(String.valueOf(" + javaExpr + "));");
    }

    private void generateIf(IfNode node) {
        String condition = generateExpression(node.condition());
        line("if (" + wrapBooleanCondition(node.condition(), condition) + ") {");
        indent++;
        generateBodyNodes(node.thenBody().children());
        indent--;
        if (node.elseBody() != null) {
            line("} else {");
            indent++;
            generateBodyNodes(node.elseBody().children());
            indent--;
        }
        line("}");
    }

    private void generateFor(ForNode node) {
        String collection = generateExpression(node.collection());
        line("for (var " + node.variableName() + " : " + collection + ") {");
        indent++;
        generateBodyNodes(node.body().children());
        indent--;
        line("}");
    }

    private void generateInclude(IncludeNode node) {
        line("// TODO: include \"" + escapeJavaString(node.fileName()) + "\"");
    }

    private void generateWidgetCall(ComponentCallNode node) {
        String beanName = widgetBeanName(node.componentName());
        line("{");
        indent++;
        line("CandiComponent _comp = _applicationContext.getBean(\"" + beanName + "\", CandiComponent.class);");
        if (!node.params().isEmpty()) {
            line("Map<String, Object> _params = new HashMap<>();");
            for (var entry : node.params().entrySet()) {
                String value = generateExpression(entry.getValue());
                line("_params.put(\"" + escapeJavaString(entry.getKey()) + "\", " + value + ");");
            }
            line("_comp.setParams(_params);");
        }
        line("_comp.render(out);");
        indent--;
        line("}");
    }

    private void generateContent(ContentNode node) {
        line("slots.renderSlot(\"content\", out);");
    }

    // ========== Expression Code Generation ==========

    private String generateExpression(Expression expr) {
        return switch (expr) {
            case Expression.Variable v -> {
                if (isField(v.name())) {
                    yield "this." + v.name();
                }
                yield v.name();
            }
            case Expression.StringLiteral s -> "\"" + escapeJavaString(s.value()) + "\"";
            case Expression.NumberLiteral n -> n.value();
            case Expression.BooleanLiteral b -> String.valueOf(b.value());
            case Expression.PropertyAccess p -> {
                String obj = generateExpression(p.object());
                String getter = toGetter(p.property());
                yield obj + "." + getter + "()";
            }
            case Expression.NullSafePropertyAccess p -> {
                String obj = generateExpression(p.object());
                String getter = toGetter(p.property());
                yield "(" + obj + " == null ? null : " + obj + "." + getter + "())";
            }
            case Expression.MethodCall m -> {
                String obj = generateExpression(m.object());
                String args = m.arguments().stream()
                        .map(this::generateExpression)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                yield obj + "." + m.methodName() + "(" + args + ")";
            }
            case Expression.NullSafeMethodCall m -> {
                String obj = generateExpression(m.object());
                String args = m.arguments().stream()
                        .map(this::generateExpression)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                yield "(" + obj + " == null ? null : " + obj + "." + m.methodName() + "(" + args + "))";
            }
            case Expression.BinaryOp b -> {
                String left = generateExpression(b.left());
                String right = generateExpression(b.right());
                if ("==".equals(b.operator())) {
                    yield "Objects.equals(" + left + ", " + right + ")";
                } else if ("!=".equals(b.operator())) {
                    yield "!Objects.equals(" + left + ", " + right + ")";
                } else {
                    yield "(" + left + " " + b.operator() + " " + right + ")";
                }
            }
            case Expression.UnaryNot u -> {
                String operand = generateExpression(u.operand());
                yield "!(" + operand + ")";
            }
            case Expression.Grouped g -> "(" + generateExpression(g.inner()) + ")";
        };
    }

    private String wrapBooleanCondition(Expression expr, String javaExpr) {
        if (expr instanceof Expression.BinaryOp || expr instanceof Expression.UnaryNot) {
            return javaExpr;
        }
        if (expr instanceof Expression.BooleanLiteral) {
            return javaExpr;
        }
        if (expr instanceof Expression.MethodCall) {
            return javaExpr;
        }
        return javaExpr + " != null && !Boolean.FALSE.equals(" + javaExpr + ")";
    }

    // ========== Helpers ==========

    private boolean isField(String name) {
        return page.fieldNames().contains(name);
    }

    private String toGetter(String property) {
        return "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
    }

    private String layoutFieldName(String layoutName) {
        return layoutName + "Layout";
    }

    private String widgetBeanName(String widgetName) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : widgetName.toCharArray()) {
            if (c == '-' || c == '_') {
                nextUpper = true;
            } else {
                sb.append(nextUpper ? Character.toUpperCase(c) : c);
                nextUpper = false;
            }
        }
        return sb.toString() + "__Widget";
    }

    /**
     * Check if a closing brace is the last one in the source (the class closing brace).
     */
    private boolean isLastClosingBrace(String[] lines, String currentLine) {
        boolean found = false;
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].trim().equals("}")) {
                if (lines[i] == currentLine || lines[i].equals(currentLine)) {
                    return i == findLastClosingBraceIndex(lines);
                }
            }
        }
        return false;
    }

    private int findLastClosingBraceIndex(String[] lines) {
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].trim().equals("}")) {
                return i;
            }
        }
        return -1;
    }

    private boolean hasComponentCalls() {
        return hasComponentCallsInBody(page.body());
    }

    private boolean hasComponentCallsInBody(BodyNode body) {
        if (body == null) return false;
        for (Node node : body.children()) {
            if (node instanceof ComponentCallNode) return true;
            if (node instanceof IfNode ifNode) {
                if (hasComponentCallsInBody(ifNode.thenBody())) return true;
                if (ifNode.elseBody() != null && hasComponentCallsInBody(ifNode.elseBody())) return true;
            }
            if (node instanceof ForNode forNode) {
                if (hasComponentCallsInBody(forNode.body())) return true;
            }
        }
        return false;
    }

    private void line(String text) {
        for (int i = 0; i < indent; i++) {
            sb.append("    ");
        }
        sb.append(text).append("\n");
    }

    static String escapeJavaString(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
