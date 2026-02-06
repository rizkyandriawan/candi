package candi.compiler.codegen;

import candi.compiler.ast.*;
import candi.compiler.expr.Expression;

import java.util.*;

/**
 * Generates Java source code from the page AST.
 * Each .page.html becomes one Java class implementing CandiPage.
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
        generateClassOpen();
        generateFields();
        generateInit();
        generateHandleAction();
        generateRender();
        generateRenderFragment();
        generateFragmentMethods();
        generateClassClose();
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
        line("import candi.runtime.CandiPage;");
        line("import candi.runtime.ActionResult;");
        line("import candi.runtime.HtmlOutput;");
        line("import candi.runtime.CandiRoute;");
        if (!page.fragments().isEmpty()) {
            line("import candi.runtime.FragmentNotFoundException;");
        }
        if (page.layout() != null) {
            line("import candi.runtime.CandiLayout;");
            line("import candi.runtime.SlotProvider;");
        }
        if (hasComponentCalls()) {
            line("import candi.runtime.CandiComponent;");
            line("import org.springframework.context.ApplicationContext;");
        }
        line("import java.util.Objects;");
        if (hasComponentCalls()) {
            line("import java.util.Map;");
            line("import java.util.HashMap;");
        }
        line("");
    }

    private void generateClassOpen() {
        line("@Component");
        line("@Scope(WebApplicationContext.SCOPE_REQUEST)");

        // Build methods array
        Set<String> methods = new LinkedHashSet<>();
        methods.add("GET");
        for (ActionNode action : page.actions()) {
            methods.add(action.method());
        }
        String methodsStr = methods.stream()
                .map(m -> "\"" + m + "\"")
                .reduce((a, b) -> a + ", " + b)
                .orElse("\"GET\"");

        line("@CandiRoute(path = \"" + escapeJavaString(page.path()) + "\", methods = {" + methodsStr + "})");
        line("public class " + className + " implements CandiPage {");
        indent++;
    }

    private void generateFields() {
        // Injected fields
        for (InjectNode inject : page.injects()) {
            line("");
            line("@Autowired");
            line("private " + inject.typeName() + " " + inject.variableName() + ";");
        }

        // ApplicationContext for component instantiation
        if (hasComponentCalls()) {
            line("");
            line("@Autowired");
            line("private ApplicationContext applicationContext;");
        }

        // Layout reference
        if (page.layout() != null) {
            line("");
            line("@Autowired");
            line("private CandiLayout " + layoutFieldName(page.layout().layoutName()) + ";");
        }

        // Init block fields — we need to extract variable declarations from the init code
        // For MVP, we'll declare fields as Object type and let the init block assign them
        // The init code is raw Java, so we parse simple assignments: var = expr;
        if (page.init() != null) {
            Set<String> declaredVars = extractInitVariables(page.init().code());
            for (String var : declaredVars) {
                line("private Object " + var + ";");
            }
        }
        line("");
    }

    private void generateInit() {
        line("@Override");
        line("public void init() {");
        indent++;
        if (page.init() != null) {
            // Emit the init code, converting simple assignments to field assignments
            String code = page.init().code();
            for (String codeLine : code.split("\n")) {
                String trimmed = codeLine.trim();
                if (!trimmed.isEmpty()) {
                    // Prefix simple assignments with "this."
                    if (isSimpleAssignment(trimmed)) {
                        line("this." + trimmed);
                    } else {
                        line(trimmed);
                    }
                }
            }
        }
        indent--;
        line("}");
        line("");
    }

    private void generateHandleAction() {
        line("@Override");
        line("public ActionResult handleAction(String method) {");
        indent++;
        for (ActionNode action : page.actions()) {
            line("if (\"" + action.method() + "\".equals(method)) {");
            indent++;
            // Emit action code, converting redirect() calls
            String code = action.code();
            for (String codeLine : code.split("\n")) {
                String trimmed = codeLine.trim();
                if (!trimmed.isEmpty()) {
                    if (trimmed.startsWith("redirect(")) {
                        // Convert redirect("url") to return ActionResult.redirect("url")
                        line("return ActionResult." + trimmed);
                    } else if (trimmed.startsWith("this.") || isSimpleMethodCall(trimmed)) {
                        line("this." + trimmed);
                    } else {
                        line(trimmed);
                    }
                }
            }
            indent--;
            line("}");
        }
        line("return ActionResult.methodNotAllowed();");
        indent--;
        line("}");
        line("");
    }

    private void generateRender() {
        line("@Override");
        line("public void render(HtmlOutput out) {");
        indent++;
        if (page.layout() != null) {
            // Delegate to layout, passing slot content as lambda
            String layoutField = layoutFieldName(page.layout().layoutName());
            line(layoutField + ".render(out, (slotName, slotOut) -> {");
            indent++;
            line("switch (slotName) {");
            indent++;
            for (SlotFillNode slot : page.slotFills()) {
                line("case \"" + escapeJavaString(slot.slotName()) + "\" -> {");
                indent++;
                generateBodyNodes(slot.body().children());
                indent--;
                line("}");
            }
            // Default slot: render the page body
            line("default -> {");
            indent++;
            if (page.body() != null) {
                generateBodyNodes(page.body().children());
            }
            indent--;
            line("}");
            indent--;
            line("}");
            indent--;
            line("});");
        } else if (page.body() != null) {
            generateBodyNodes(page.body().children());
        }
        indent--;
        line("}");
        line("");
    }

    private void generateRenderFragment() {
        if (page.fragments().isEmpty()) return;

        line("@Override");
        line("public void renderFragment(String name, HtmlOutput out) {");
        indent++;
        for (FragmentDefNode fragment : page.fragments()) {
            String methodName = fragmentMethodName(fragment.name());
            line("if (\"" + escapeJavaString(fragment.name()) + "\".equals(name)) {");
            indent++;
            line(methodName + "(out);");
            line("return;");
            indent--;
            line("}");
        }
        line("throw new FragmentNotFoundException(name);");
        indent--;
        line("}");
        line("");
    }

    private void generateFragmentMethods() {
        for (FragmentDefNode fragment : page.fragments()) {
            String methodName = fragmentMethodName(fragment.name());
            line("private void " + methodName + "(HtmlOutput out) {");
            indent++;
            generateBodyNodes(fragment.body().children());
            indent--;
            line("}");
            line("");
        }
    }

    private void generateClassClose() {
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
            case FragmentCallNode call -> generateFragmentCall(call);
            case ComponentCallNode call -> generateComponentCall(call);
            case SlotRenderNode slot -> generateSlotRender(slot);
            default -> throw new IllegalStateException("Unexpected node in body: " + node.getClass());
        }
    }

    private void generateHtml(HtmlNode html) {
        // Split into lines for readability, but emit as single append for performance
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

    private void generateFragmentCall(FragmentCallNode node) {
        String methodName = fragmentMethodName(node.fragmentName());
        line(methodName + "(out);");
    }

    private void generateComponentCall(ComponentCallNode node) {
        String beanName = componentBeanName(node.componentName());
        line("{");
        indent++;
        line("CandiComponent _comp = applicationContext.getBean(\"" + beanName + "\", CandiComponent.class);");
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

    private void generateSlotRender(SlotRenderNode node) {
        line("slots.renderSlot(\"" + escapeJavaString(node.slotName()) + "\", out);");
    }

    // ========== Expression Code Generation ==========

    private String generateExpression(Expression expr) {
        return switch (expr) {
            case Expression.Variable v -> {
                // Check if it's a field (from @inject or @init)
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

    /**
     * Wrap a condition expression for boolean evaluation.
     * If the expression is already boolean (comparison, &&, ||, !), use as-is.
     * Otherwise, generate a truthiness check.
     */
    private String wrapBooleanCondition(Expression expr, String javaExpr) {
        if (expr instanceof Expression.BinaryOp || expr instanceof Expression.UnaryNot) {
            return javaExpr;
        }
        if (expr instanceof Expression.BooleanLiteral) {
            return javaExpr;
        }
        // For variables and property accesses that might be Boolean objects
        // We generate a truthiness test
        return javaExpr + " != null && !Boolean.FALSE.equals(" + javaExpr + ")";
    }

    // ========== Helpers ==========

    private boolean isField(String name) {
        for (InjectNode inject : page.injects()) {
            if (inject.variableName().equals(name)) return true;
        }
        if (page.init() != null) {
            Set<String> vars = extractInitVariables(page.init().code());
            if (vars.contains(name)) return true;
        }
        return false;
    }

    /**
     * Extract variable names from init code.
     * Looks for patterns like: varName = expr;
     */
    public static Set<String> extractInitVariables(String code) {
        Set<String> vars = new LinkedHashSet<>();
        for (String line : code.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            // Match: identifier = something;
            int eqIdx = trimmed.indexOf('=');
            if (eqIdx > 0 && eqIdx < trimmed.length() - 1 && trimmed.charAt(eqIdx + 1) != '=') {
                String lhs = trimmed.substring(0, eqIdx).trim();
                // lhs should be a simple identifier (no dots, no type prefix)
                if (isSimpleIdentifier(lhs)) {
                    vars.add(lhs);
                }
            }
        }
        return vars;
    }

    private static boolean isSimpleIdentifier(String s) {
        if (s.isEmpty() || !Character.isJavaIdentifierStart(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
        }
        return true;
    }

    private boolean isSimpleAssignment(String line) {
        // Match: varName = expr;
        int eqIdx = line.indexOf('=');
        if (eqIdx > 0 && eqIdx < line.length() - 1 && line.charAt(eqIdx + 1) != '=') {
            String lhs = line.substring(0, eqIdx).trim();
            return isSimpleIdentifier(lhs);
        }
        return false;
    }

    private boolean isSimpleMethodCall(String line) {
        // Check if line starts with an identifier followed by .method(
        int dotIdx = line.indexOf('.');
        if (dotIdx > 0) {
            String before = line.substring(0, dotIdx).trim();
            return isSimpleIdentifier(before);
        }
        return false;
    }

    private String toGetter(String property) {
        return "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
    }

    private String fragmentMethodName(String fragmentName) {
        // Convert "post-content" to "renderFragment_postContent"
        String safe = fragmentName.replace("-", "_");
        // camelCase after underscores
        StringBuilder result = new StringBuilder("renderFragment_");
        boolean nextUpper = false;
        for (char c : safe.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else {
                result.append(nextUpper ? Character.toUpperCase(c) : c);
                nextUpper = false;
            }
        }
        return result.toString();
    }

    private void line(String text) {
        for (int i = 0; i < indent; i++) {
            sb.append("    ");
        }
        sb.append(text).append("\n");
    }

    private boolean hasComponentCalls() {
        return hasComponentCallsInBody(page.body()) ||
               page.fragments().stream().anyMatch(f -> hasComponentCallsInBody(f.body())) ||
               page.slotFills().stream().anyMatch(s -> hasComponentCallsInBody(s.body()));
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

    private String layoutFieldName(String layoutName) {
        // "base" → "baseLayout"
        return layoutName + "Layout";
    }

    private String componentBeanName(String componentName) {
        // "card" → "card__Component"
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
