package candi.compiler.codegen;

import candi.compiler.JavaAnalyzer;
import candi.compiler.ast.BlockNode;
import candi.compiler.ast.BodyNode;
import candi.compiler.ast.FragmentNode;

import java.util.*;

/**
 * Generates a {@code _Candi} subclass that extends the user's class and implements
 * the appropriate Candi interface (CandiPage, CandiLayout, or CandiComponent).
 *
 * <p>Unlike {@link CodeGenerator} which replaces the user's class, this generator
 * produces a separate subclass. The user's class stays untouched. Spring registers
 * the generated subclass (which has {@code @Component}).
 *
 * <p>Key differences from CodeGenerator:
 * <ul>
 *   <li>Generates {@code X_Candi extends X implements CandiPage}</li>
 *   <li>No user code copying — everything is inherited</li>
 *   <li>Field access via getters: {@code this.getTitle()} not {@code this.title}</li>
 *   <li>Widget setParams via setters: {@code this.setType(val)} not {@code this.type = val}</li>
 * </ul>
 */
public class SubclassCodeGenerator {

    /**
     * Input data for subclass generation. All information needed to generate the _Candi class.
     * Replaces PageNode as the input — the annotation processor constructs this directly.
     */
    public record SubclassInput(
            String userClassName,
            String packageName,
            JavaAnalyzer.FileType fileType,
            String pagePath,          // from @Page, nullable
            String layoutName,        // from @Page(layout=...) or derived from class name for layouts
            Set<String> fieldNames,
            Map<String, String> fieldTypes,
            Set<String> actionMethods,  // "POST", "DELETE", etc.
            BodyNode body
    ) {}

    private final SubclassInput input;
    private final StringBuilder sb = new StringBuilder();
    private int indent = 0;
    private final BodyRenderer bodyRenderer;

    public SubclassCodeGenerator(SubclassInput input) {
        this.input = input;
        this.bodyRenderer = new BodyRenderer(
                input.fieldNames, BodyRenderer.GETTER_SETTER, sb, 0);
    }

    public String generate() {
        generatePackage();
        generateImports();

        switch (input.fileType) {
            case PAGE -> generatePageClass();
            case LAYOUT -> generateLayoutClass();
            case WIDGET -> generateWidgetClass();
        }

        return sb.toString();
    }

    private void generatePackage() {
        if (input.packageName != null && !input.packageName.isEmpty()) {
            line("package " + input.packageName + ";");
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
        if (bodyRenderer.hasComponentCallsInBody(input.body)) {
            line("import org.springframework.context.ApplicationContext;");
            line("import java.util.Map;");
            line("import java.util.HashMap;");
        }
        line("");
    }

    // ========== PAGE Generation ==========

    private void generatePageClass() {
        Set<String> methods = new LinkedHashSet<>();
        methods.add("GET");
        methods.addAll(input.actionMethods);

        String methodsStr = methods.stream()
                .map(m -> "\"" + m + "\"")
                .reduce((a, b) -> a + ", " + b)
                .orElse("\"GET\"");

        line("@Component");
        line("@Scope(WebApplicationContext.SCOPE_REQUEST)");
        if (input.pagePath != null) {
            line("@CandiRoute(path = \"" + CodeGenerator.escapeJavaString(input.pagePath) + "\", methods = {" + methodsStr + "})");
        }

        String generatedClassName = input.userClassName + "_Candi";
        line("public class " + generatedClassName + " extends " + input.userClassName + " implements CandiPage {");
        indent++;

        if (bodyRenderer.hasComponentCallsInBody(input.body)) {
            line("");
            line("@Autowired");
            line("private ApplicationContext _applicationContext;");
        }

        if (input.layoutName != null) {
            line("");
            line("@Autowired");
            line("private CandiLayout " + layoutFieldName(input.layoutName) + ";");
        }

        line("");
        generatePageRender();
        generateFragmentMethods();

        indent--;
        line("}");
    }

    private void generatePageRender() {
        line("@Override");
        line("public void render(HtmlOutput out) {");
        indent++;
        bodyRenderer.setIndent(indent);
        if (input.layoutName != null) {
            String layoutField = layoutFieldName(input.layoutName);
            List<BlockNode> blocks = bodyRenderer.collectBlocks(input.body);
            line(layoutField + ".render(out, (slotName, slotOut) -> {");
            indent++;
            bodyRenderer.setIndent(indent);
            if (blocks.isEmpty()) {
                line("if (\"content\".equals(slotName)) {");
                indent++;
                bodyRenderer.setIndent(indent);
                if (input.body != null) {
                    bodyRenderer.renderBodyNodes(input.body.children());
                }
                indent--;
                bodyRenderer.setIndent(indent);
                line("}");
            } else {
                line("switch (slotName) {");
                indent++;
                bodyRenderer.setIndent(indent);
                line("case \"content\" -> {");
                indent++;
                bodyRenderer.setIndent(indent);
                if (input.body != null) {
                    bodyRenderer.renderBodyNodes(input.body.children());
                }
                indent--;
                bodyRenderer.setIndent(indent);
                line("}");
                for (BlockNode block : blocks) {
                    line("case \"" + CodeGenerator.escapeJavaString(block.name()) + "\" -> {");
                    indent++;
                    bodyRenderer.setIndent(indent);
                    bodyRenderer.renderBodyNodes(block.body().children());
                    indent--;
                    bodyRenderer.setIndent(indent);
                    line("}");
                }
                line("default -> {}");
                indent--;
                bodyRenderer.setIndent(indent);
                line("}");
            }
            indent--;
            bodyRenderer.setIndent(indent);
            line("});");
        } else if (input.body != null) {
            bodyRenderer.renderBodyNodes(input.body.children());
        }
        indent--;
        bodyRenderer.setIndent(indent);
        line("}");
    }

    private void generateFragmentMethods() {
        List<FragmentNode> fragments = bodyRenderer.collectFragments(input.body);
        bodyRenderer.setIndent(indent);
        bodyRenderer.renderFragmentMethods(fragments);
    }

    // ========== LAYOUT Generation ==========

    private void generateLayoutClass() {
        String layoutBeanName = input.layoutName;
        line("@Component(\"" + CodeGenerator.escapeJavaString(layoutBeanName) + "Layout\")");

        String generatedClassName = input.userClassName + "_Candi";
        line("public class " + generatedClassName + " extends " + input.userClassName + " implements CandiLayout {");
        indent++;

        line("");
        generateLayoutRender();

        indent--;
        line("}");
    }

    private void generateLayoutRender() {
        line("@Override");
        line("public void render(HtmlOutput out, SlotProvider slots) {");
        indent++;
        bodyRenderer.setIndent(indent);
        if (input.body != null) {
            bodyRenderer.renderBodyNodes(input.body.children());
        }
        indent--;
        bodyRenderer.setIndent(indent);
        line("}");
    }

    // ========== WIDGET Generation ==========

    private void generateWidgetClass() {
        String beanName = input.userClassName + "__Widget";
        line("@Component(\"" + CodeGenerator.escapeJavaString(beanName) + "\")");
        line("@Scope(\"prototype\")");

        String generatedClassName = input.userClassName + "_Candi";
        line("public class " + generatedClassName + " extends " + input.userClassName + " implements CandiComponent {");
        indent++;

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
        bodyRenderer.setIndent(indent);
        for (String fieldName : input.fieldNames) {
            String type = input.fieldTypes.get(fieldName);
            if (type != null) {
                line("if (params.containsKey(\"" + fieldName + "\")) {");
                indent++;
                bodyRenderer.setIndent(indent);
                String castExpr = "(" + type + ") params.get(\"" + fieldName + "\")";
                line(BodyRenderer.GETTER_SETTER.writeField(fieldName, castExpr));
                indent--;
                bodyRenderer.setIndent(indent);
                line("}");
            }
        }
        indent--;
        bodyRenderer.setIndent(indent);
        line("}");
    }

    private void generateWidgetRender() {
        line("@Override");
        line("public void render(HtmlOutput out) {");
        indent++;
        bodyRenderer.setIndent(indent);
        if (input.body != null) {
            bodyRenderer.renderBodyNodes(input.body.children());
        }
        indent--;
        bodyRenderer.setIndent(indent);
        line("}");
    }

    // ========== Helpers ==========

    private String layoutFieldName(String layoutName) {
        return layoutName + "Layout";
    }

    private void line(String text) {
        for (int i = 0; i < indent; i++) {
            sb.append("    ");
        }
        sb.append(text).append("\n");
    }
}
