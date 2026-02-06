package candi.compiler;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple scanner that extracts metadata from the Java class portion of a .jhtml file.
 * NOT a full Java parser — uses regex patterns to extract class-level information.
 */
public class JavaAnalyzer {

    /**
     * The type of Candi file, determined by class-level annotation.
     */
    public enum FileType {
        PAGE,     // @Page("/path")
        LAYOUT,   // @Layout
        WIDGET    // @Widget
    }

    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "(?:public\\s+)?class\\s+(\\w+)");
    private static final Pattern PAGE_ANNOTATION = Pattern.compile(
            "@Page\\s*\\(");
    private static final Pattern PAGE_VALUE = Pattern.compile(
            "@Page\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"");
    private static final Pattern PAGE_LAYOUT = Pattern.compile(
            "@Page\\s*\\([^)]*layout\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern LAYOUT_ANNOTATION = Pattern.compile(
            "@Layout\\b");
    private static final Pattern WIDGET_ANNOTATION = Pattern.compile(
            "@Widget\\b");
    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "private\\s+(\\S+(?:<[^>]+>)?)\\s+(\\w+)\\s*;");
    private static final Pattern AUTOWIRED_PATTERN = Pattern.compile(
            "@Autowired\\s*\\n\\s*private\\s+(\\S+(?:<[^>]+>)?)\\s+(\\w+)\\s*;");
    private static final Pattern ACTION_ANNOTATION = Pattern.compile(
            "@(Post|Put|Delete|Patch)\\b");

    /**
     * Result of analyzing a Java class source.
     */
    public record ClassInfo(
            String className,
            FileType fileType,            // determined by annotation
            String pagePath,              // from @Page, nullable
            String layoutName,            // from @Page(layout=...) or @Layout, nullable
            Set<String> fieldNames,
            Map<String, String> fieldTypes,  // fieldName → typeName
            Set<String> autowiredFields,
            Set<String> actionMethods        // HTTP methods: "POST", "DELETE", etc.
    ) {}

    /**
     * Analyze the Java source section of a .jhtml file.
     */
    public ClassInfo analyze(String javaSource) {
        String className = extractClassName(javaSource);
        FileType fileType = detectFileType(javaSource);
        String pagePath = extractPagePath(javaSource);
        String layoutName = extractLayoutName(javaSource, fileType, className);

        Set<String> fieldNames = new LinkedHashSet<>();
        Map<String, String> fieldTypes = new LinkedHashMap<>();
        Set<String> autowiredFields = new LinkedHashSet<>();

        // Extract @Autowired fields
        Matcher autowiredMatcher = AUTOWIRED_PATTERN.matcher(javaSource);
        while (autowiredMatcher.find()) {
            String type = autowiredMatcher.group(1);
            String name = autowiredMatcher.group(2);
            fieldNames.add(name);
            fieldTypes.put(name, type);
            autowiredFields.add(name);
        }

        // Extract all private fields (including non-autowired)
        Matcher fieldMatcher = FIELD_PATTERN.matcher(javaSource);
        while (fieldMatcher.find()) {
            String type = fieldMatcher.group(1);
            String name = fieldMatcher.group(2);
            fieldNames.add(name);
            fieldTypes.put(name, type);
        }

        // Extract action method annotations
        Set<String> actionMethods = new LinkedHashSet<>();
        Matcher actionMatcher = ACTION_ANNOTATION.matcher(javaSource);
        while (actionMatcher.find()) {
            String annotation = actionMatcher.group(1);
            actionMethods.add(annotation.toUpperCase());
        }

        return new ClassInfo(className, fileType, pagePath, layoutName, fieldNames, fieldTypes,
                autowiredFields, actionMethods);
    }

    /**
     * Detect file type from class-level annotations.
     * @Page → PAGE, @Layout → LAYOUT, @Widget → WIDGET.
     * Defaults to PAGE if no annotation found (backward compat for body-only files).
     */
    private FileType detectFileType(String source) {
        if (WIDGET_ANNOTATION.matcher(source).find()) {
            return FileType.WIDGET;
        }
        if (LAYOUT_ANNOTATION.matcher(source).find() && !PAGE_ANNOTATION.matcher(source).find()) {
            // @Layout without @Page means this IS a layout declaration
            return FileType.LAYOUT;
        }
        return FileType.PAGE;
    }

    private String extractClassName(String source) {
        Matcher m = CLASS_PATTERN.matcher(source);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String extractPagePath(String source) {
        Matcher m = PAGE_VALUE.matcher(source);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * Extract the layout name.
     * For PAGE: from @Page(layout="name")
     * For LAYOUT: derived from class name (e.g. BaseLayout → "base")
     */
    private String extractLayoutName(String source, FileType fileType, String className) {
        if (fileType == FileType.LAYOUT && className != null) {
            // Layout name = class name minus "Layout" suffix, lowercased
            String name = className;
            if (name.endsWith("Layout")) {
                name = name.substring(0, name.length() - "Layout".length());
            }
            return name.substring(0, 1).toLowerCase() + name.substring(1);
        }

        if (fileType == FileType.PAGE) {
            Matcher m = PAGE_LAYOUT.matcher(source);
            if (m.find()) {
                return m.group(1);
            }
        }

        return null;
    }
}
