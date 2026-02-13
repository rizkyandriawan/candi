package candi.compiler.ast;

import candi.compiler.SourceLocation;

/**
 * {{ push "scripts" }}<script src="..."></script>{{ end }}
 *
 * Used in pages to push content to a named stack (rendered by {{ stack "..." }} in layout).
 */
public record PushNode(
        String name,
        BodyNode body,
        SourceLocation location
) implements Node {
}
