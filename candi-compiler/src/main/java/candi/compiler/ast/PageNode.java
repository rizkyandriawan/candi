package candi.compiler.ast;

import candi.compiler.JavaAnalyzer;
import candi.compiler.SourceLocation;

import java.util.Map;
import java.util.Set;

/**
 * Root AST node for a .jhtml file.
 * Contains both the raw Java source and the parsed template body.
 */
public record PageNode(
        String javaSource,          // raw Java class code
        String className,           // extracted class name
        JavaAnalyzer.FileType fileType,  // PAGE, LAYOUT, or WIDGET
        String pagePath,            // from @Page, nullable
        String layoutName,          // from @Page(layout=...) or derived from class name
        Set<String> fieldNames,     // all declared fields
        Map<String, String> fieldTypes,  // fieldName → typeName
        BodyNode body,
        SourceLocation location
) implements Node {
}
