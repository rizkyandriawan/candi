package candi.compiler.ast;

import candi.compiler.SourceLocation;
import candi.compiler.expr.Expression;

import java.util.List;

/**
 * {{ switch expr }}{{ case "a" }}...{{ case "b" }}...{{ default }}...{{ end }}
 */
public record SwitchNode(
        Expression subject,
        List<CaseBranch> cases,
        BodyNode defaultBody,  // nullable
        SourceLocation location
) implements Node {

    public record CaseBranch(Expression value, BodyNode body) {}
}
