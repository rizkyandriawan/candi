package candi.compiler.ast;

import candi.compiler.SourceLocation;

/**
 * @init { code } block. Code is stored as raw Java source.
 */
public record InitNode(String code, SourceLocation location) implements Node {
}
