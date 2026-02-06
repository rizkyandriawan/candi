package candi.compiler.type;

import candi.compiler.SourceLocation;

/**
 * A type checking error with source location information.
 */
public record TypeCheckError(String message, SourceLocation location) {

    @Override
    public String toString() {
        if (location != null) {
            return location.file() + ":" + location.line() + ":" + location.column() + " — " + message;
        }
        return message;
    }
}
