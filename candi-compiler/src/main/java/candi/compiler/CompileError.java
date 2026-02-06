package candi.compiler;

public class CompileError extends RuntimeException {

    private final SourceLocation location;

    public CompileError(String message, SourceLocation location) {
        super(location + ": " + message);
        this.location = location;
    }

    public CompileError(String message, SourceLocation location, Throwable cause) {
        super(location + ": " + message, cause);
        this.location = location;
    }

    public SourceLocation getLocation() {
        return location;
    }
}
