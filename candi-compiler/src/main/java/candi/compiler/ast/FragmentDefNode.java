package candi.compiler.ast;

import candi.compiler.SourceLocation;

/**
 * @fragment "name" { body } block. Body is template content (HTML + expressions).
 */
public record FragmentDefNode(String name, BodyNode body, SourceLocation location) implements Node {
}
