package candi.compiler.ast;

import candi.compiler.SourceLocation;

/**
 * @action METHOD { code } block.
 */
public record ActionNode(String method, String code, SourceLocation location) implements Node {
}
