package candi.compiler.ast;

import candi.compiler.SourceLocation;

public sealed interface Node permits
        PageNode, InjectNode, InitNode, ActionNode, FragmentDefNode,
        LayoutDirectiveNode, SlotFillNode,
        BodyNode, HtmlNode, ExpressionOutputNode, RawExpressionOutputNode,
        IfNode, ForNode, FragmentCallNode, ComponentCallNode, SlotRenderNode {

    SourceLocation location();
}
