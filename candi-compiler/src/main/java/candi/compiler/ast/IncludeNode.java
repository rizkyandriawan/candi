package candi.compiler.ast;

import candi.compiler.SourceLocation;
import candi.compiler.expr.Expression;

import java.util.Map;

/**
 * {{ include "file" param1=expr1 param2=expr2 }}
 * Inline include of an HTML partial file with optional parameters.
 */
public record IncludeNode(
        String fileName,
        Map<String, Expression> params,
        SourceLocation location
) implements Node {
}
