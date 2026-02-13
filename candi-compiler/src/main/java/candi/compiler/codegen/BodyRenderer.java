package candi.compiler.codegen;

import candi.compiler.ast.*;
import candi.compiler.expr.Expression;

import java.util.ArrayList;
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
    private int tempVarCounter = 0;

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
            case FragmentNode fragment -> renderFragment(fragment);
            case ContentNode content -> renderContent(content);
            case SetNode set -> renderSet(set);
            case SwitchNode sw -> renderSwitch(sw);
            case SlotNode slot -> renderSlot(slot);
            case BlockNode block -> renderBlock(block);
            case StackNode stack -> renderStack(stack);
            case PushNode push -> renderPush(push);
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
        String varName = node.variableName();
        String listVar = "_list_" + varName;
        String indexVar = varName + "_index";
        String firstVar = varName + "_first";
        String lastVar = varName + "_last";
        String sizeVar = "_size_" + varName;
        String collVar = "_c_" + varName;
        line("{");
        indent++;
        line("var " + listVar + " = " + collection + ";");
        line("int " + indexVar + " = 0;");
        line("int " + sizeVar + " = (" + listVar + " instanceof java.util.Collection<?> " + collVar + ") ? " + collVar + ".size() : 0;");
        line("for (var " + varName + " : " + listVar + ") {");
        indent++;
        line("boolean " + firstVar + " = (" + indexVar + " == 0);");
        line("boolean " + lastVar + " = (" + indexVar + " == " + sizeVar + " - 1);");
        renderBodyNodes(node.body().children());
        line(indexVar + "++;");
        indent--;
        line("}");
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

    private void renderFragment(FragmentNode node) {
        // Inline: just render the body children as part of the normal page
        renderBodyNodes(node.body().children());
    }

    private void renderContent(ContentNode node) {
        line("slots.renderSlot(\"content\", out);");
    }

    private void renderSet(SetNode node) {
        String expr = generateExpression(node.value());
        line("var " + node.variableName() + " = " + expr + ";");
    }

    private void renderSwitch(SwitchNode node) {
        String subject = generateExpression(node.subject());
        String tmpVar = "_sw" + (tempVarCounter++);
        line("{");
        indent++;
        line("var " + tmpVar + " = " + subject + ";");
        boolean first = true;
        for (SwitchNode.CaseBranch branch : node.cases()) {
            String caseVal = generateExpression(branch.value());
            if (first) {
                line("if (java.util.Objects.equals(" + tmpVar + ", " + caseVal + ")) {");
                first = false;
            } else {
                line("} else if (java.util.Objects.equals(" + tmpVar + ", " + caseVal + ")) {");
            }
            indent++;
            renderBodyNodes(branch.body().children());
            indent--;
        }
        if (node.defaultBody() != null) {
            if (first) {
                // No case branches, only default
                renderBodyNodes(node.defaultBody().children());
            } else {
                line("} else {");
                indent++;
                renderBodyNodes(node.defaultBody().children());
                indent--;
                line("}");
            }
        } else if (!first) {
            line("}");
        }
        indent--;
        line("}");
    }

    private void renderSlot(SlotNode node) {
        String slotName = node.name();
        // In a layout, render the slot content or fall back to default
        line("{");
        indent++;
        line("int _before = out.length();");
        line("slots.renderSlot(\"" + CodeGenerator.escapeJavaString(slotName) + "\", out);");
        line("if (out.length() == _before) {");
        indent++;
        if (node.defaultContent() != null) {
            renderBodyNodes(node.defaultContent().children());
        }
        indent--;
        line("}");
        indent--;
        line("}");
    }

    private void renderBlock(BlockNode node) {
        // Block nodes in page context are rendered inline (collected by SubclassCodeGenerator for slot dispatch)
        renderBodyNodes(node.body().children());
    }

    private void renderStack(StackNode node) {
        line("out.renderStack(\"" + CodeGenerator.escapeJavaString(node.name()) + "\");");
    }

    private void renderPush(PushNode node) {
        String tmpVar = "_push" + (tempVarCounter++);
        line("{");
        indent++;
        line("HtmlOutput " + tmpVar + " = new HtmlOutput();");
        // Render push body into temp output
        // We need to swap 'out' temporarily — but since we're generating code, we just use the temp var
        // Actually, we generate the body with tmpVar as output
        for (Node child : node.body().children()) {
            renderPushBodyNode(child, tmpVar);
        }
        line("out.pushStack(\"" + CodeGenerator.escapeJavaString(node.name()) + "\", " + tmpVar + ".toHtml());");
        indent--;
        line("}");
    }

    /**
     * Render a body node but targeting a different output variable.
     */
    private void renderPushBodyNode(Node node, String outVar) {
        switch (node) {
            case HtmlNode html -> {
                String content = html.content();
                if (!content.isEmpty()) {
                    line(outVar + ".append(\"" + CodeGenerator.escapeJavaString(content) + "\");");
                }
            }
            case ExpressionOutputNode expr -> {
                String javaExpr = generateExpression(expr.expression());
                line(outVar + ".appendEscaped(String.valueOf(" + javaExpr + "));");
            }
            case RawExpressionOutputNode raw -> {
                String javaExpr = generateExpression(raw.expression());
                line(outVar + ".append(String.valueOf(" + javaExpr + "));");
            }
            default -> {
                // For complex nodes inside push, fall back to rendering into the temp output
                // This is a simplified approach — complex nested structures in push blocks
                // would need full out-variable parameterization
                renderBodyNode(node);
            }
        }
    }

    // ========== Fragment Code Generation Helpers ==========

    /**
     * Collect all FragmentNodes from a body (recursively).
     */
    public List<FragmentNode> collectFragments(BodyNode body) {
        List<FragmentNode> fragments = new ArrayList<>();
        if (body != null) {
            collectFragmentsFromNodes(body.children(), fragments);
        }
        return fragments;
    }

    private void collectFragmentsFromNodes(List<Node> nodes, List<FragmentNode> fragments) {
        for (Node node : nodes) {
            if (node instanceof FragmentNode f) {
                fragments.add(f);
                // Also check inside fragment body for nested fragments
                collectFragmentsFromNodes(f.body().children(), fragments);
            } else if (node instanceof IfNode ifNode) {
                collectFragmentsFromNodes(ifNode.thenBody().children(), fragments);
                if (ifNode.elseBody() != null) {
                    collectFragmentsFromNodes(ifNode.elseBody().children(), fragments);
                }
            } else if (node instanceof ForNode forNode) {
                collectFragmentsFromNodes(forNode.body().children(), fragments);
            }
        }
    }

    /**
     * Collect all BlockNodes from a body (recursively).
     */
    public List<BlockNode> collectBlocks(BodyNode body) {
        List<BlockNode> blocks = new ArrayList<>();
        if (body != null) {
            collectBlocksFromNodes(body.children(), blocks);
        }
        return blocks;
    }

    private void collectBlocksFromNodes(List<Node> nodes, List<BlockNode> blocks) {
        for (Node node : nodes) {
            if (node instanceof BlockNode b) {
                blocks.add(b);
            } else if (node instanceof IfNode ifNode) {
                collectBlocksFromNodes(ifNode.thenBody().children(), blocks);
                if (ifNode.elseBody() != null) {
                    collectBlocksFromNodes(ifNode.elseBody().children(), blocks);
                }
            } else if (node instanceof ForNode forNode) {
                collectBlocksFromNodes(forNode.body().children(), blocks);
            }
        }
    }

    /**
     * Convert a fragment name to a valid Java method suffix.
     * "post-list" → "post_list"
     */
    public static String fragmentMethodSuffix(String name) {
        return name.replace('-', '_');
    }

    /**
     * Generate renderFragment() dispatch method and per-fragment private methods.
     */
    public void renderFragmentMethods(List<FragmentNode> fragments) {
        if (fragments.isEmpty()) return;

        // Generate dispatch method
        line("");
        line("@Override");
        line("public void renderFragment(String _name, HtmlOutput out) {");
        indent++;
        setIndent(indent);
        line("switch (_name) {");
        indent++;
        setIndent(indent);
        for (FragmentNode f : fragments) {
            line("case \"" + CodeGenerator.escapeJavaString(f.name()) + "\" -> renderFragment_" + fragmentMethodSuffix(f.name()) + "(out);");
        }
        line("default -> throw new IllegalArgumentException(\"Unknown fragment: \" + _name);");
        indent--;
        setIndent(indent);
        line("}");
        indent--;
        setIndent(indent);
        line("}");

        // Generate per-fragment methods
        for (FragmentNode f : fragments) {
            line("");
            line("private void renderFragment_" + fragmentMethodSuffix(f.name()) + "(HtmlOutput out) {");
            indent++;
            setIndent(indent);
            renderBodyNodes(f.body().children());
            indent--;
            setIndent(indent);
            line("}");
        }
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
                } else if ("~".equals(b.operator())) {
                    yield "(String.valueOf(" + left + ") + String.valueOf(" + right + "))";
                } else {
                    yield "(" + left + " " + b.operator() + " " + right + ")";
                }
            }
            case Expression.UnaryNot u -> {
                String operand = generateExpression(u.operand());
                yield "!(" + operand + ")";
            }
            case Expression.UnaryMinus u -> {
                String operand = generateExpression(u.operand());
                yield "(-(" + operand + "))";
            }
            case Expression.Grouped g -> "(" + generateExpression(g.inner()) + ")";
            case Expression.Ternary t -> {
                String cond = generateExpression(t.condition());
                String then = generateExpression(t.thenExpr());
                String els = generateExpression(t.elseExpr());
                yield "(" + wrapBooleanCondition(t.condition(), cond) + " ? " + then + " : " + els + ")";
            }
            case Expression.NullCoalesce nc -> {
                String left = generateExpression(nc.left());
                String fallback = generateExpression(nc.fallback());
                yield "(" + left + " != null ? " + left + " : " + fallback + ")";
            }
            case Expression.FilterCall f -> {
                String input = generateExpression(f.input());
                String filterName = f.filterName();
                // Map "default" filter name to "defaultVal" to avoid Java keyword clash
                String methodName = "default".equals(filterName) ? "defaultVal" : filterName;
                if (f.arguments().isEmpty()) {
                    yield "candi.runtime.CandiFilters." + methodName + "(" + input + ")";
                } else {
                    String args = f.arguments().stream()
                            .map(this::generateExpression)
                            .reduce((a, b2) -> a + ", " + b2)
                            .orElse("");
                    yield "candi.runtime.CandiFilters." + methodName + "(" + input + ", " + args + ")";
                }
            }
            case Expression.IndexAccess ia -> {
                String obj = generateExpression(ia.object());
                String idx = generateExpression(ia.index());
                yield "candi.runtime.CandiRuntime.index(" + obj + ", " + idx + ")";
            }
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
        if (expr instanceof Expression.Ternary) {
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
            if (node instanceof FragmentNode fragmentNode) {
                if (hasComponentCallsInBody(fragmentNode.body())) return true;
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
