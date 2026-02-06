package candi.compiler;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple scanner that extracts metadata from the Java class portion of a .page.html file.
 * NOT a full Java parser — uses regex patterns to extract class-level information.
 */
public class JavaAnalyzer {

    private static final Pattern CLASS_PATTERN = Pattern.compile(
            "(?:public\\s+)?class\\s+(\\w+)");
    private static final Pattern PAGE_ANNOTATION = Pattern.compile(
            "@Page\\s*\\(\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern LAYOUT_ANNOTATION = Pattern.compile(
            "@Layout\\s*\\(\\s*\"([^\"]+)\"\\s*\\)");
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
            String pagePath,          // from @Page, nullable
            String layoutName,        // from @Layout, nullable
            Set<String> fieldNames,
            Map<String, String> fieldTypes,  // fieldName → typeName
            Set<String> autowiredFields,
            Set<String> actionMethods        // HTTP methods: "POST", "DELETE", etc.
    ) {}

    /**
     * Analyze the Java source section of a .page.html file.
     */
    public ClassInfo analyze(String javaSource) {
        String className = extractClassName(javaSource);
        String pagePath = extractPagePath(javaSource);
        String layoutName = extractLayoutName(javaSource);

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

        return new ClassInfo(className, pagePath, layoutName, fieldNames, fieldTypes,
                autowiredFields, actionMethods);
    }

    private String extractClassName(String source) {
        Matcher m = CLASS_PATTERN.matcher(source);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String extractPagePath(String source) {
        Matcher m = PAGE_ANNOTATION.matcher(source);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String extractLayoutName(String source) {
        Matcher m = LAYOUT_ANNOTATION.matcher(source);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}
