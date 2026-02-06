package candi.compiler.ast;

import candi.compiler.SourceLocation;

public sealed interface Node permits
        PageNode, IncludeNode, ContentNode,
        BodyNode, HtmlNode, ExpressionOutputNode, RawExpressionOutputNode,
        IfNode, ForNode, ComponentCallNode {

    SourceLocation location();
}
