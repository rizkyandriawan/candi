package candi.compiler.ast;

import candi.compiler.SourceLocation;

import java.util.List;

/**
 * Root AST node for a .page.html file.
 */
public record PageNode(
        String path,
        List<InjectNode> injects,
        InitNode init,
        List<ActionNode> actions,
        List<FragmentDefNode> fragments,
        LayoutDirectiveNode layout,
        List<SlotFillNode> slotFills,
        BodyNode body,
        SourceLocation location
) implements Node {
}
