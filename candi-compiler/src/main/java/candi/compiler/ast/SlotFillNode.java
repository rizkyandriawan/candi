package candi.compiler.ast;

import candi.compiler.SourceLocation;

public record SlotFillNode(String slotName, BodyNode body, SourceLocation location) implements Node {
}
