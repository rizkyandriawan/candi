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
     * Metadata for a field annotated with @RequestParam.
     */
    public record RequestParamInfo(String paramName, String defaultValue, boolean required) {}

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
            BodyNode body,
            Map<String, RequestParamInfo> requestParams,  // fieldName -> info
            Map<String, String> pathVariables,             // fieldName -> varName
            Set<String> pageableFields,                    // fields of type Pageable
            boolean hasInitMethod                          // whether parent has init()
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
        if (hasParamBindings()) {
            line("import jakarta.servlet.http.HttpServletRequest;");
            if (!input.pathVariables.isEmpty()) {
                line("import org.springframework.web.servlet.HandlerMapping;");
            }
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

        if (hasParamBindings()) {
            line("");
            line("@Autowired");
            line("private HttpServletRequest _request;");
        }

        if (input.layoutName != null) {
            line("");
            line("@Autowired");
            line("private CandiLayout " + layoutFieldName(input.layoutName) + ";");
        }

        if (hasParamBindings()) {
            line("");
            generateInitOverride();
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

    // ========== Parameter Binding ==========

    private boolean hasParamBindings() {
        return !input.requestParams.isEmpty()
                || !input.pathVariables.isEmpty()
                || !input.pageableFields.isEmpty();
    }

    private void generateInitOverride() {
        line("@Override");
        line("public void init() {");
        indent++;

        // @PathVariable bindings
        if (!input.pathVariables.isEmpty()) {
            line("@SuppressWarnings(\"unchecked\")");
            line("java.util.Map<String, String> _pathVars = (java.util.Map<String, String>)");
            line("    _request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);");
            for (Map.Entry<String, String> entry : input.pathVariables.entrySet()) {
                String fieldName = entry.getKey();
                String varName = entry.getValue();
                String fieldType = input.fieldTypes.get(fieldName);
                line("{");
                indent++;
                line("String _raw = _pathVars != null ? _pathVars.get(\"" + varName + "\") : null;");
                generateFieldAssignment(fieldName, fieldType, null, false);
                indent--;
                line("}");
            }
        }

        // @RequestParam bindings
        for (Map.Entry<String, RequestParamInfo> entry : input.requestParams.entrySet()) {
            String fieldName = entry.getKey();
            RequestParamInfo info = entry.getValue();
            String fieldType = input.fieldTypes.get(fieldName);
            line("{");
            indent++;
            line("String _raw = _request.getParameter(\"" + info.paramName() + "\");");
            if (info.defaultValue() != null) {
                line("if (_raw == null || _raw.isEmpty()) _raw = \"" +
                        CodeGenerator.escapeJavaString(info.defaultValue()) + "\";");
            } else if (info.required()) {
                line("if (_raw == null) throw new IllegalArgumentException(\"Required parameter '" +
                        info.paramName() + "' is missing\");");
            }
            generateFieldAssignment(fieldName, fieldType, info.defaultValue(), info.required());
            indent--;
            line("}");
        }

        // Pageable bindings
        for (String fieldName : input.pageableFields) {
            line("{");
            indent++;
            line("int _page = 0; int _size = 20;");
            line("String _pageRaw = _request.getParameter(\"page\");");
            line("String _sizeRaw = _request.getParameter(\"size\");");
            line("if (_pageRaw != null && !_pageRaw.isEmpty()) _page = Integer.parseInt(_pageRaw);");
            line("if (_sizeRaw != null && !_sizeRaw.isEmpty()) _size = Integer.parseInt(_sizeRaw);");
            line("String _sortRaw = _request.getParameter(\"sort\");");
            line("org.springframework.data.domain.Sort _sort = org.springframework.data.domain.Sort.unsorted();");
            line("if (_sortRaw != null && !_sortRaw.isEmpty()) {");
            indent++;
            line("String[] _parts = _sortRaw.split(\",\");");
            line("_sort = _parts.length == 2");
            line("    ? org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.fromString(_parts[1]), _parts[0])");
            line("    : org.springframework.data.domain.Sort.by(_parts[0]);");
            indent--;
            line("}");
            String setter = "this.set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            line(setter + "(org.springframework.data.domain.PageRequest.of(_page, _size, _sort));");
            indent--;
            line("}");
        }

        if (input.hasInitMethod()) {
            line("super.init();");
        }
        indent--;
        line("}");
    }

    private void generateFieldAssignment(String fieldName, String fieldType, String defaultValue, boolean required) {
        String setter = "this.set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        if (fieldType == null) fieldType = "String";

        switch (fieldType) {
            case "String" -> line("if (_raw != null) " + setter + "(_raw);");
            case "int" -> line("if (_raw != null && !_raw.isEmpty()) " + setter + "(Integer.parseInt(_raw));");
            case "Integer" -> line("if (_raw != null && !_raw.isEmpty()) " + setter + "(Integer.valueOf(_raw));");
            case "long" -> line("if (_raw != null && !_raw.isEmpty()) " + setter + "(Long.parseLong(_raw));");
            case "Long" -> line("if (_raw != null && !_raw.isEmpty()) " + setter + "(Long.valueOf(_raw));");
            case "double" -> line("if (_raw != null && !_raw.isEmpty()) " + setter + "(Double.parseDouble(_raw));");
            case "Double" -> line("if (_raw != null && !_raw.isEmpty()) " + setter + "(Double.valueOf(_raw));");
            case "boolean" -> line("if (_raw != null && !_raw.isEmpty()) " + setter + "(Boolean.parseBoolean(_raw));");
            case "Boolean" -> line("if (_raw != null && !_raw.isEmpty()) " + setter + "(Boolean.valueOf(_raw));");
            default -> line("if (_raw != null) " + setter + "(_raw);");
        }
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
