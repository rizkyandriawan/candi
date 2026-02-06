package candi.compiler.ast;

import candi.compiler.SourceLocation;

import java.util.Map;
import java.util.Set;

/**
 * Root AST node for a .page.html file (v2 syntax).
 * Contains both the raw Java source and the parsed template body.
 */
public record PageNode(
        String javaSource,          // raw Java class code
        String className,           // extracted class name
        String pagePath,            // from @Page, nullable
        String layoutName,          // from @Layout, nullable
        Set<String> fieldNames,     // all declared fields
        Map<String, String> fieldTypes,  // fieldName → typeName
        BodyNode body,
        SourceLocation location
) implements Node {
}
