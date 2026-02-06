package candi.compiler.codegen;

import candi.compiler.ast.*;
import candi.compiler.expr.Expression;

import java.util.*;

/**
 * Generates Java source code from a v2 page AST.
 * Takes the user's Java class and injects:
 * - implements CandiPage
 * - @Component @Scope(REQUEST) annotations
 * - @CandiRoute annotation
 * - import statements
 * - render(HtmlOutput out) method from template
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
     * Generate the class by transforming the user's Java source:
     * - Add class-level annotations
     * - Add 'implements CandiPage'
     * - Inject render() method before closing brace
     * - For layouts: inject render(HtmlOutput, SlotProvider) instead
     */
    private void generateClassWithRender() {
        String javaSource = page.javaSource();

        if (javaSource == null || javaSource.isEmpty()) {
            // Body-only page (include file) — generate minimal class
            generateMinimalClass();
            return;
        }

        // Build methods set for @CandiRoute
        Set<String> methods = new LinkedHashSet<>();
        methods.add("GET");
        for (String action : page.fieldNames()) {
            // Action methods are detected from annotations, not field names
        }
        // Check the Java source for action annotations
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

        // Transform the class declaration: add "implements CandiPage"
        // Find the class line and modify it
        String[] lines = javaSource.split("\n");
        boolean classStarted = false;
        boolean needsApplicationContext = hasComponentCalls() &&
                !javaSource.contains("ApplicationContext");

        for (String srcLine : lines) {
            String trimmed = srcLine.trim();

            // Skip package/import statements — already generated above
            if (trimmed.startsWith("package ") || trimmed.startsWith("import ")) {
                continue;
            }

            // Skip user annotations that we handle ourselves
            if (trimmed.startsWith("@Page(") || trimmed.startsWith("@Layout(")) {
                continue;
            }

            // Transform class declaration
            if (!classStarted && trimmed.contains("class " + page.className())) {
                String modifiedLine = trimmed;
                // Add 'implements CandiPage' if not already present
                if (!modifiedLine.contains("implements")) {
                    modifiedLine = modifiedLine.replace("{", "implements CandiPage {");
                } else if (!modifiedLine.contains("CandiPage")) {
                    modifiedLine = modifiedLine.replace("implements ", "implements CandiPage, ");
                }
                line(modifiedLine);
                classStarted = true;
                indent++;

                // Inject ApplicationContext if needed for components
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

            // Skip the closing brace of the class — we'll add render() then close
            if (classStarted && trimmed.equals("}") && isLastClosingBrace(lines, srcLine)) {
                continue;
            }

            // Emit the line as-is (preserving user's code)
            if (classStarted) {
                line(trimmed);
            } else {
                line(srcLine);
            }
        }

        // Generate render method
        line("");
        generateRender();

        // Close class
        indent--;
        line("}");
    }

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
        generateRender();
        indent--;
        line("}");
    }

    private void generateRender() {
        line("@Override");
        line("public void render(HtmlOutput out) {");
        indent++;
        if (page.layoutName() != null) {
            // Delegate to layout, passing content as lambda
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
            case ComponentCallNode call -> generateComponentCall(call);
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
        // Include is rendered at compile time — the include file contents are inlined.
        // For now, emit a comment placeholder. The actual inlining is done by CandiCompiler.
        // If the include wasn't resolved, emit a comment.
        line("// TODO: include \"" + escapeJavaString(node.fileName()) + "\"");
    }

    private void generateComponentCall(ComponentCallNode node) {
        String beanName = componentBeanName(node.componentName());
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

    private String componentBeanName(String componentName) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : componentName.toCharArray()) {
            if (c == '-' || c == '_') {
                nextUpper = true;
            } else {
                sb.append(nextUpper ? Character.toUpperCase(c) : c);
                nextUpper = false;
            }
        }
        return sb.toString() + "__Component";
    }

    /**
     * Check if a closing brace is the last one in the source (the class closing brace).
     */
    private boolean isLastClosingBrace(String[] lines, String currentLine) {
        // Find this line and check if there are no more closing braces after it
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
