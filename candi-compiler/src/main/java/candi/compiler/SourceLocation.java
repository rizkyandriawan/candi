package candi.compiler;

public record SourceLocation(String file, int line, int column) {

    @Override
    public String toString() {
        return file + ":" + line + ":" + column;
    }
}
