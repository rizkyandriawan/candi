package candi.compiler.ast;

import candi.compiler.SourceLocation;

/**
 * {{ fragment "name" }} ... {{ end }}
 *
 * During normal rendering, the body is rendered inline.
 * For AJAX fragment requests, the body can be rendered in isolation.
 */
public record FragmentNode(
        String name,
        BodyNode body,
        SourceLocation location
) implements Node {
}
