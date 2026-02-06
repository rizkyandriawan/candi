package candi.compiler.ast;

import candi.compiler.SourceLocation;

/**
 * {{ fragment "name" }} — inline fragment call.
 */
public record FragmentCallNode(String fragmentName, SourceLocation location) implements Node {
}
