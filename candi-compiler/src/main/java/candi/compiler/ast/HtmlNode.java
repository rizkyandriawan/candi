package candi.compiler.ast;

import candi.compiler.SourceLocation;

public record HtmlNode(String content, SourceLocation location) implements Node {
}
