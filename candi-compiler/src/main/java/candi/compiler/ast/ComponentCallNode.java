package candi.compiler.ast;

import candi.compiler.SourceLocation;
import candi.compiler.expr.Expression;

import java.util.Map;

/**
 * {{ component "name" param1=expr1 param2=expr2 }}
 */
public record ComponentCallNode(
        String componentName,
        Map<String, Expression> params,
        SourceLocation location
) implements Node {
}
