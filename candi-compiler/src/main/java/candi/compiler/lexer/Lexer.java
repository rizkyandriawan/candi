package candi.compiler.lexer;

import candi.compiler.CompileError;
import candi.compiler.SourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Lexer for .page.html files.
 * <p>
 * Two modes:
 * - HEADER: before the first HTML tag. Scans @directives and {} code blocks.
 * - BODY: after first HTML tag (or after all directives). Scans HTML and {{ }} expressions.
 */
public class Lexer {

    private final String source;
    private final String fileName;
    private int pos;
    private int line;
    private int col;
    private boolean inBody;
    private final List<Token> tokens = new ArrayList<>();

    public Lexer(String source, String fileName) {
        this.source = source;
        this.fileName = fileName;
        this.pos = 0;
        this.line = 1;
        this.col = 1;
        this.inBody = false;
    }

    public List<Token> tokenize() {
        while (!isAtEnd()) {
            if (inBody) {
                lexBody();
            } else {
                lexHeader();
            }
        }
        tokens.add(new Token(TokenType.EOF, "", loc()));
        return tokens;
    }

    // ========== HEADER MODE ==========

    private void lexHeader() {
        skipWhitespaceAndNewlines();
        if (isAtEnd()) return;

        // Check if we've hit HTML content or template expressions (transition to body mode)
        if (peek() == '<' && !isDirectiveAhead()) {
            inBody = true;
            return;
        }

        // {{ at the start means body-only page (no header directives before HTML)
        if (lookingAt("{{")) {
            inBody = true;
            return;
        }

        if (peek() == '@') {
            lexDirective();
        } else {
            // Anything that's not a directive or HTML — error
            throw error("Unexpected character '" + peek() + "' in header section");
        }
    }

    private boolean isDirectiveAhead() {
        // Check if < is followed by something that looks like an HTML tag
        // vs being part of an expression (shouldn't happen in header)
        if (pos + 1 >= source.length()) return false;
        char next = source.charAt(pos + 1);
        return next == '@'; // Not really HTML
    }

    private void lexDirective() {
        SourceLocation start = loc();
        advance(); // skip @

        String keyword = readIdentifier();
        switch (keyword) {
            case "page" -> lexPageDirective(start);
            case "inject" -> lexInjectDirective(start);
            case "init" -> lexInitDirective(start);
            case "action" -> lexActionDirective(start);
            case "fragment" -> lexFragmentDefDirective(start);
            case "layout" -> lexLayoutDirective(start);
            case "slot" -> lexSlotFillDirective(start);
            default -> throw error("Unknown directive '@" + keyword + "'", start);
        }
    }

    private void lexPageDirective(SourceLocation start) {
        tokens.add(new Token(TokenType.PAGE, "@page", start));
        skipWhitespace();
        tokens.add(readStringLiteral());
    }

    private void lexInjectDirective(SourceLocation start) {
        tokens.add(new Token(TokenType.INJECT, "@inject", start));
        skipWhitespace();
        // Type name (may include generics like List<Post>)
        SourceLocation typeLoc = loc();
        String typeName = readTypeName();
        tokens.add(new Token(TokenType.IDENTIFIER, typeName, typeLoc));
        skipWhitespace();
        // Variable name
        SourceLocation varLoc = loc();
        String varName = readIdentifier();
        tokens.add(new Token(TokenType.IDENTIFIER, varName, varLoc));
    }

    private void lexInitDirective(SourceLocation start) {
        tokens.add(new Token(TokenType.INIT, "@init", start));
        skipWhitespace();
        tokens.add(readCodeBlock());
    }

    private void lexActionDirective(SourceLocation start) {
        tokens.add(new Token(TokenType.ACTION, "@action", start));
        skipWhitespace();
        // HTTP method
        SourceLocation methodLoc = loc();
        String method = readIdentifier();
        tokens.add(new Token(TokenType.HTTP_METHOD, method, methodLoc));
        skipWhitespace();
        tokens.add(readCodeBlock());
    }

    private void lexFragmentDefDirective(SourceLocation start) {
        tokens.add(new Token(TokenType.FRAGMENT_DEF, "@fragment", start));
        skipWhitespace();
        tokens.add(readStringLiteral());
        skipWhitespace();
        // Fragment body is a code block containing HTML template
        tokens.add(readFragmentBlock());
    }

    private void lexLayoutDirective(SourceLocation start) {
        tokens.add(new Token(TokenType.LAYOUT, "@layout", start));
        skipWhitespace();
        tokens.add(readStringLiteral());
    }

    private void lexSlotFillDirective(SourceLocation start) {
        tokens.add(new Token(TokenType.SLOT_FILL, "@slot", start));
        skipWhitespace();
        SourceLocation nameLoc = loc();
        String name = readIdentifier();
        tokens.add(new Token(TokenType.IDENTIFIER, name, nameLoc));
        skipWhitespace();
        tokens.add(readFragmentBlock());
    }

    // ========== BODY MODE ==========

    private void lexBody() {
        if (isAtEnd()) return;

        if (lookingAt("{{")) {
            lexTemplateExpression();
        } else {
            lexHtml();
        }
    }

    private void lexHtml() {
        SourceLocation start = loc();
        StringBuilder sb = new StringBuilder();
        while (!isAtEnd() && !lookingAt("{{")) {
            sb.append(peek());
            advance();
        }
        if (!sb.isEmpty()) {
            tokens.add(new Token(TokenType.HTML, sb.toString(), start));
        }
    }

    private void lexTemplateExpression() {
        SourceLocation start = loc();
        advance(); // {
        advance(); // {
        tokens.add(new Token(TokenType.EXPR_START, "{{", start));

        skipWhitespace();

        // Check for keywords
        if (isAtEnd() || lookingAt("}}")) {
            tokens.add(new Token(TokenType.EXPR_END, "}}", loc()));
            if (!isAtEnd()) { advance(); advance(); }
            return;
        }

        String word = peekIdentifier();
        switch (word) {
            case "if" -> {
                advance("if".length());
                tokens.add(new Token(TokenType.KEYWORD_IF, "if", start));
                skipWhitespace();
                lexExpressionTokensUntilClose();
            }
            case "else" -> {
                advance("else".length());
                tokens.add(new Token(TokenType.KEYWORD_ELSE, "else", start));
                skipWhitespace();
                // Check for "else if"
                if (peekIdentifier().equals("if")) {
                    SourceLocation ifLoc = loc();
                    advance("if".length());
                    tokens.add(new Token(TokenType.KEYWORD_IF, "if", ifLoc));
                    skipWhitespace();
                    lexExpressionTokensUntilClose();
                }
            }
            case "end" -> {
                advance("end".length());
                tokens.add(new Token(TokenType.KEYWORD_END, "end", start));
            }
            case "for" -> {
                advance("for".length());
                tokens.add(new Token(TokenType.KEYWORD_FOR, "for", start));
                skipWhitespace();
                // Read: variable in collection
                SourceLocation varLoc = loc();
                String varName = readIdentifier();
                tokens.add(new Token(TokenType.IDENTIFIER, varName, varLoc));
                skipWhitespace();
                // "in" keyword
                SourceLocation inLoc = loc();
                String inKw = readIdentifier();
                if (!"in".equals(inKw)) {
                    throw error("Expected 'in' in for loop, got '" + inKw + "'", inLoc);
                }
                tokens.add(new Token(TokenType.KEYWORD_IN, "in", inLoc));
                skipWhitespace();
                lexExpressionTokensUntilClose();
            }
            case "raw" -> {
                advance("raw".length());
                tokens.add(new Token(TokenType.KEYWORD_RAW, "raw", start));
                skipWhitespace();
                lexExpressionTokensUntilClose();
            }
            case "fragment" -> {
                advance("fragment".length());
                tokens.add(new Token(TokenType.KEYWORD_FRAGMENT, "fragment", start));
                skipWhitespace();
                // Fragment name as string literal
                tokens.add(readStringLiteral());
                skipWhitespace();
            }
            case "component" -> {
                advance("component".length());
                tokens.add(new Token(TokenType.KEYWORD_COMPONENT, "component", start));
                skipWhitespace();
                // Component name as string literal
                tokens.add(readStringLiteral());
                skipWhitespace();
                // Optional params: key=value
                lexComponentParams();
            }
            case "slot" -> {
                advance("slot".length());
                tokens.add(new Token(TokenType.KEYWORD_SLOT, "slot", start));
                skipWhitespace();
                if (!lookingAt("}}")) {
                    SourceLocation nameLoc = loc();
                    String name = readIdentifier();
                    tokens.add(new Token(TokenType.IDENTIFIER, name, nameLoc));
                    skipWhitespace();
                }
            }
            default -> {
                // Regular expression output
                lexExpressionTokensUntilClose();
            }
        }

        // Consume closing }}
        skipWhitespace();
        if (lookingAt("}}")) {
            SourceLocation endLoc = loc();
            advance();
            advance();
            tokens.add(new Token(TokenType.EXPR_END, "}}", endLoc));
        } else {
            throw error("Expected '}}' to close template expression");
        }
    }

    private void lexComponentParams() {
        while (!isAtEnd() && !lookingAt("}}")) {
            skipWhitespace();
            if (lookingAt("}}")) break;

            // key=value
            SourceLocation keyLoc = loc();
            String key = readIdentifier();
            tokens.add(new Token(TokenType.IDENTIFIER, key, keyLoc));

            skipWhitespace();
            if (peek() == '=') {
                SourceLocation eqLoc = loc();
                advance();
                tokens.add(new Token(TokenType.EQUALS_SIGN, "=", eqLoc));
                skipWhitespace();
                // Value is an expression (read tokens until next whitespace or }})
                lexSingleExpressionTokens();
            }
            skipWhitespace();
        }
    }

    private void lexSingleExpressionTokens() {
        // Lex expression tokens for a single expression (stops at whitespace or }})
        while (!isAtEnd() && !lookingAt("}}") && !isWhitespace(peek())) {
            lexOneExpressionToken();
        }
    }

    private void lexExpressionTokensUntilClose() {
        while (!isAtEnd() && !lookingAt("}}")) {
            skipWhitespace();
            if (lookingAt("}}")) break;
            lexOneExpressionToken();
        }
    }

    private void lexOneExpressionToken() {
        skipWhitespace();
        if (isAtEnd() || lookingAt("}}")) return;

        SourceLocation tokLoc = loc();
        char c = peek();

        switch (c) {
            case '.' -> {
                advance();
                if (!isAtEnd() && peek() == '?') {
                    // This shouldn't happen — ?. has ? first
                    tokens.add(new Token(TokenType.DOT, ".", tokLoc));
                } else {
                    tokens.add(new Token(TokenType.DOT, ".", tokLoc));
                }
            }
            case '?' -> {
                advance();
                if (!isAtEnd() && peek() == '.') {
                    advance();
                    tokens.add(new Token(TokenType.NULL_SAFE_DOT, "?.", tokLoc));
                } else {
                    throw error("Expected '.' after '?'", tokLoc);
                }
            }
            case '(' -> {
                advance();
                tokens.add(new Token(TokenType.LPAREN, "(", tokLoc));
            }
            case ')' -> {
                advance();
                tokens.add(new Token(TokenType.RPAREN, ")", tokLoc));
            }
            case ',' -> {
                advance();
                tokens.add(new Token(TokenType.COMMA, ",", tokLoc));
            }
            case '=' -> {
                advance();
                if (!isAtEnd() && peek() == '=') {
                    advance();
                    tokens.add(new Token(TokenType.EQ, "==", tokLoc));
                } else {
                    tokens.add(new Token(TokenType.EQUALS_SIGN, "=", tokLoc));
                }
            }
            case '!' -> {
                advance();
                if (!isAtEnd() && peek() == '=') {
                    advance();
                    tokens.add(new Token(TokenType.NEQ, "!=", tokLoc));
                } else {
                    tokens.add(new Token(TokenType.NOT, "!", tokLoc));
                }
            }
            case '<' -> {
                advance();
                if (!isAtEnd() && peek() == '=') {
                    advance();
                    tokens.add(new Token(TokenType.LTE, "<=", tokLoc));
                } else {
                    tokens.add(new Token(TokenType.LT, "<", tokLoc));
                }
            }
            case '>' -> {
                advance();
                if (!isAtEnd() && peek() == '=') {
                    advance();
                    tokens.add(new Token(TokenType.GTE, ">=", tokLoc));
                } else {
                    tokens.add(new Token(TokenType.GT, ">", tokLoc));
                }
            }
            case '&' -> {
                advance();
                if (!isAtEnd() && peek() == '&') {
                    advance();
                    tokens.add(new Token(TokenType.AND, "&&", tokLoc));
                } else {
                    throw error("Expected '&&', got single '&'", tokLoc);
                }
            }
            case '|' -> {
                advance();
                if (!isAtEnd() && peek() == '|') {
                    advance();
                    tokens.add(new Token(TokenType.OR, "||", tokLoc));
                } else {
                    throw error("Expected '||', got single '|'", tokLoc);
                }
            }
            case '"' -> {
                tokens.add(readStringLiteral());
            }
            default -> {
                if (Character.isDigit(c)) {
                    tokens.add(readNumber());
                } else if (Character.isJavaIdentifierStart(c)) {
                    String ident = readIdentifier();
                    TokenType type = switch (ident) {
                        case "true" -> TokenType.TRUE;
                        case "false" -> TokenType.FALSE;
                        default -> TokenType.IDENTIFIER;
                    };
                    tokens.add(new Token(type, ident, tokLoc));
                } else {
                    throw error("Unexpected character '" + c + "' in expression");
                }
            }
        }
    }

    // ========== HELPER METHODS ==========

    private Token readStringLiteral() {
        SourceLocation start = loc();
        if (peek() != '"') {
            throw error("Expected '\"'");
        }
        advance(); // skip opening "
        StringBuilder sb = new StringBuilder();
        while (!isAtEnd() && peek() != '"') {
            if (peek() == '\\') {
                advance();
                if (isAtEnd()) throw error("Unterminated string literal", start);
                switch (peek()) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    default -> {
                        sb.append('\\');
                        sb.append(peek());
                    }
                }
            } else {
                sb.append(peek());
            }
            advance();
        }
        if (isAtEnd()) {
            throw error("Unterminated string literal", start);
        }
        advance(); // skip closing "
        return new Token(TokenType.STRING_LITERAL, sb.toString(), start);
    }

    private Token readNumber() {
        SourceLocation start = loc();
        StringBuilder sb = new StringBuilder();
        while (!isAtEnd() && (Character.isDigit(peek()) || peek() == '.')) {
            sb.append(peek());
            advance();
        }
        return new Token(TokenType.NUMBER, sb.toString(), start);
    }

    private Token readCodeBlock() {
        SourceLocation start = loc();
        if (peek() != '{') {
            throw error("Expected '{'");
        }
        advance(); // skip opening {
        StringBuilder sb = new StringBuilder();
        int depth = 1;
        while (!isAtEnd() && depth > 0) {
            char c = peek();
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) break;
            }
            sb.append(c);
            advance();
        }
        if (isAtEnd() && depth > 0) {
            throw error("Unterminated code block", start);
        }
        advance(); // skip closing }
        return new Token(TokenType.CODE_BLOCK, sb.toString().trim(), start);
    }

    /**
     * Read a fragment body block. This is a brace-matched block that contains
     * HTML template content (with {{ }} expressions), not Java code.
     * We store it as a CODE_BLOCK token and the parser will re-lex it as body content.
     */
    private Token readFragmentBlock() {
        SourceLocation start = loc();
        if (peek() != '{') {
            throw error("Expected '{'");
        }
        advance(); // skip opening {
        StringBuilder sb = new StringBuilder();
        int depth = 1;
        while (!isAtEnd() && depth > 0) {
            char c = peek();
            if (c == '{') {
                // Check for {{ which is a template expression, not a brace increase
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '{') {
                    sb.append("{{");
                    advance();
                    advance();
                    // Now scan until }}
                    while (!isAtEnd()) {
                        if (lookingAt("}}")) {
                            sb.append("}}");
                            advance();
                            advance();
                            break;
                        }
                        sb.append(peek());
                        advance();
                    }
                    continue;
                }
                depth++;
            } else if (c == '}') {
                // Check for }} which is a template expression close
                if (pos + 1 < source.length() && source.charAt(pos + 1) == '}') {
                    sb.append("}}");
                    advance();
                    advance();
                    continue;
                }
                depth--;
                if (depth == 0) break;
            }
            sb.append(c);
            advance();
        }
        if (isAtEnd() && depth > 0) {
            throw error("Unterminated fragment block", start);
        }
        advance(); // skip closing }
        return new Token(TokenType.CODE_BLOCK, sb.toString().trim(), start);
    }

    private String readIdentifier() {
        StringBuilder sb = new StringBuilder();
        while (!isAtEnd() && Character.isJavaIdentifierPart(peek())) {
            sb.append(peek());
            advance();
        }
        if (sb.isEmpty()) {
            throw error("Expected identifier");
        }
        return sb.toString();
    }

    private String readTypeName() {
        // Read type name including generics: e.g., List<Post>, Map<String, Post>
        StringBuilder sb = new StringBuilder();
        while (!isAtEnd() && Character.isJavaIdentifierPart(peek())) {
            sb.append(peek());
            advance();
        }
        // Check for generics
        if (!isAtEnd() && peek() == '<') {
            sb.append(peek());
            advance();
            int depth = 1;
            while (!isAtEnd() && depth > 0) {
                char c = peek();
                if (c == '<') depth++;
                else if (c == '>') depth--;
                sb.append(c);
                advance();
            }
        }
        if (sb.isEmpty()) {
            throw error("Expected type name");
        }
        return sb.toString();
    }

    private String peekIdentifier() {
        int saved = pos;
        StringBuilder sb = new StringBuilder();
        while (saved < source.length() && Character.isJavaIdentifierPart(source.charAt(saved))) {
            sb.append(source.charAt(saved));
            saved++;
        }
        return sb.toString();
    }

    private char peek() {
        return source.charAt(pos);
    }

    private boolean lookingAt(String s) {
        return source.startsWith(s, pos);
    }

    private void advance() {
        if (pos < source.length()) {
            if (source.charAt(pos) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
            pos++;
        }
    }

    private void advance(int count) {
        for (int i = 0; i < count; i++) {
            advance();
        }
    }

    private boolean isAtEnd() {
        return pos >= source.length();
    }

    private void skipWhitespace() {
        while (!isAtEnd() && isWhitespace(peek()) && peek() != '\n') {
            advance();
        }
    }

    private void skipWhitespaceAndNewlines() {
        while (!isAtEnd() && isWhitespace(peek())) {
            advance();
        }
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    private boolean isDirective() {
        return peek() == '@';
    }

    private SourceLocation loc() {
        return new SourceLocation(fileName, line, col);
    }

    private CompileError error(String message) {
        return new CompileError(message, loc());
    }

    private CompileError error(String message, SourceLocation loc) {
        return new CompileError(message, loc);
    }
}
