package candi.compiler.lexer;

import candi.compiler.SourceLocation;

public record Token(TokenType type, String value, SourceLocation location) {

    @Override
    public String toString() {
        if (value == null || value.isEmpty()) {
            return type.name() + " @ " + location;
        }
        String display = value.length() > 40 ? value.substring(0, 40) + "..." : value;
        return type.name() + "(\"" + display.replace("\n", "\\n") + "\") @ " + location;
    }
}
