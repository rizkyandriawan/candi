package candi.compiler.codegen;

import candi.compiler.ast.*;
import candi.compiler.expr.Expression;

import java.util.List;
import java.util.Set;

/**
 * Shared body/expression rendering logic used by both CodeGenerator and SubclassCodeGenerator.
 * Parameterized by a field access strategy (direct vs getter).
 */
public class BodyRenderer {

    /**
     * Strategy for accessing fields in generated code.
     */
    public interface FieldAccessStrategy {
        /** Generate code to read a field value. */
        String readField(String fieldName);

        /** Generate code to write a field value (for setParams). */
        String writeField(String fieldName, String valueExpr);
    }

    /** Direct field access: this.field / this.field = value */
    public static final FieldAccessStrategy DIRECT = new FieldAccessStrategy() {
        @Override
        public String readField(String fieldName) {
            return "this." + fieldName;
        }

        @Override
        public String writeField(String fieldName, String valueExpr) {
            return "this." + fieldName + " = " + valueExpr + ";";
        }
    };

    /** Getter/setter access: this.getField() / this.setField(value) */
    public static final FieldAccessStrategy GETTER_SETTER = new FieldAccessStrategy() {
        @Override
        public String readField(String fieldName) {
            return "this.get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1) + "()";
        }

        @Override
        public String writeField(String fieldName, String valueExpr) {
            return "this.set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1) + "(" + valueExpr + ");";
        }
    };

    private final Set<String> fieldNames;
    private final FieldAccessStrategy fieldAccess;
    private final StringBuilder sb;
    private int indent;

    public BodyRenderer(Set<String> fieldNames, FieldAccessStrategy fieldAccess, StringBuilder sb, int indent) {
        this.fieldNames = fieldNames;
        this.fieldAccess = fieldAccess;
        this.sb = sb;
        this.indent = indent;
    }

    public void setIndent(int indent) {
        this.indent = indent;
    }

    public int getIndent() {
        return indent;
    }

    public void renderBodyNodes(List<Node> nodes) {
        for (Node node : nodes) {
            renderBodyNode(node);
        }
    }

    private void renderBodyNode(Node node) {
        switch (node) {
            case HtmlNode html -> renderHtml(html);
            case ExpressionOutputNode expr -> renderExpressionOutput(expr);
            case RawExpressionOutputNode raw -> renderRawExpressionOutput(raw);
            case IfNode ifNode -> renderIf(ifNode);
            case ForNode forNode -> renderFor(forNode);
            case IncludeNode include -> renderInclude(include);
            case ComponentCallNode call -> renderWidgetCall(call);
            case ContentNode content -> renderContent(content);
            default -> throw new IllegalStateException("Unexpected node in body: " + node.getClass());
        }
    }

    private void renderHtml(HtmlNode html) {
        String content = html.content();
        if (!content.isEmpty()) {
            line("out.append(\"" + CodeGenerator.escapeJavaString(content) + "\");");
        }
    }

    private void renderExpressionOutput(ExpressionOutputNode node) {
        String javaExpr = generateExpression(node.expression());
        line("out.appendEscaped(String.valueOf(" + javaExpr + "));");
    }

    private void renderRawExpressionOutput(RawExpressionOutputNode node) {
        String javaExpr = generateExpression(node.expression());
        line("out.append(String.valueOf(" + javaExpr + "));");
    }

    private void renderIf(IfNode node) {
        String condition = generateExpression(node.condition());
        line("if (" + wrapBooleanCondition(node.condition(), condition) + ") {");
        indent++;
        renderBodyNodes(node.thenBody().children());
        indent--;
        if (node.elseBody() != null) {
            line("} else {");
            indent++;
            renderBodyNodes(node.elseBody().children());
            indent--;
        }
        line("}");
    }

    private void renderFor(ForNode node) {
        String collection = generateExpression(node.collection());
        line("for (var " + node.variableName() + " : " + collection + ") {");
        indent++;
        renderBodyNodes(node.body().children());
        indent--;
        line("}");
    }

    private void renderInclude(IncludeNode node) {
        line("// TODO: include \"" + CodeGenerator.escapeJavaString(node.fileName()) + "\"");
    }

    private void renderWidgetCall(ComponentCallNode node) {
        String beanName = widgetBeanName(node.componentName());
        line("{");
        indent++;
        line("CandiComponent _comp = _applicationContext.getBean(\"" + beanName + "\", CandiComponent.class);");
        if (!node.params().isEmpty()) {
            line("Map<String, Object> _params = new HashMap<>();");
            for (var entry : node.params().entrySet()) {
                String value = generateExpression(entry.getValue());
                line("_params.put(\"" + CodeGenerator.escapeJavaString(entry.getKey()) + "\", " + value + ");");
            }
            line("_comp.setParams(_params);");
        }
        line("_comp.render(out);");
        indent--;
        line("}");
    }

    private void renderContent(ContentNode node) {
        line("slots.renderSlot(\"content\", out);");
    }

    // ========== Expression Code Generation ==========

    public String generateExpression(Expression expr) {
        return switch (expr) {
            case Expression.Variable v -> {
                if (fieldNames.contains(v.name())) {
                    yield fieldAccess.readField(v.name());
                }
                yield v.name();
            }
            case Expression.StringLiteral s -> "\"" + CodeGenerator.escapeJavaString(s.value()) + "\"";
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

    private String toGetter(String property) {
        return "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
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

    public boolean hasComponentCallsInBody(BodyNode body) {
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

    public void line(String text) {
        for (int i = 0; i < indent; i++) {
            sb.append("    ");
        }
        sb.append(text).append("\n");
    }
}
