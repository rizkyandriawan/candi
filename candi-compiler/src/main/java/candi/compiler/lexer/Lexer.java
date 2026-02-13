package candi.compiler.lexer;

import candi.compiler.CompileError;
import candi.compiler.SourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Lexer for .page.html files (v2 syntax).
 * <p>
 * Splits the source at {@code <template>} / {@code </template>} tags.
 * Returns the raw Java source and tokenizes only the template body.
 */
public class Lexer {

    private final String source;
    private final String fileName;
    private int pos;
    private int line;
    private int col;
    private final List<Token> tokens = new ArrayList<>();

    // Extracted by splitSource()
    private String javaSource;
    private String templateSource;
    private int templateStartLine;

    // Whitespace control: when true, trim leading whitespace from next HTML token
    private boolean trimNextHtmlLeading = false;

    public Lexer(String source, String fileName) {
        this.source = source;
        this.fileName = fileName;
        this.pos = 0;
        this.line = 1;
        this.col = 1;
    }

    /**
     * Get the raw Java source (everything before {@code <template>}).
     * Only available after tokenize() has been called.
     */
    public String getJavaSource() {
        return javaSource;
    }

    /**
     * Tokenize the template section of a .page.html file.
     * The Java source section is extracted but not tokenized — use getJavaSource().
     */
    public List<Token> tokenize() {
        splitSource();

        // Reset position to tokenize template content
        pos = 0;
        line = templateStartLine;
        col = 1;

        while (pos < templateSource.length()) {
            lexBody();
        }

        tokens.add(new Token(TokenType.EOF, "", loc()));
        return tokens;
    }

    /**
     * Tokenize template content directly (no Java/template splitting).
     * Used by the annotation processor which already has the template string
     * extracted from {@code @Template}.
     *
     * @param template the raw template content
     * @param fileName the source file name (for error reporting)
     * @return list of tokens
     */
    public static List<Token> tokenizeTemplate(String template, String fileName) {
        Lexer lexer = new Lexer(template, fileName);
        // Set template source directly — no splitting needed
        lexer.javaSource = "";
        lexer.templateSource = template;
        lexer.templateStartLine = 1;

        lexer.pos = 0;
        lexer.line = 1;
        lexer.col = 1;

        while (lexer.pos < lexer.templateSource.length()) {
            lexer.lexBody();
        }

        lexer.tokens.add(new Token(TokenType.EOF, "", lexer.loc()));
        return lexer.tokens;
    }

    /**
     * Split the source into Java section and template section.
     *
     * Supports two formats:
     * 1. @Template("""...""") annotation — Java text block (preferred)
     * 2. &lt;template&gt;...&lt;/template&gt; block after class (legacy)
     *
     * For @Template: the annotation is stripped from javaSource, template extracted from text block.
     * For &lt;template&gt;: everything before the tag is javaSource, content between tags is template.
     * If neither found, entire source is treated as template (body-only include file).
     */
    private void splitSource() {
        // Try @Template("""...""") first
        if (splitFromAnnotation()) {
            return;
        }

        // Fall back to <template> block
        int templateOpen = source.indexOf("<template>");
        if (templateOpen == -1) {
            // Body-only file (no Java class, just template — e.g. include files)
            javaSource = "";
            templateSource = source;
            templateStartLine = 1;
            return;
        }

        javaSource = source.substring(0, templateOpen).trim();

        // Count lines in java source + template tag line
        templateStartLine = 1;
        for (int i = 0; i <= templateOpen; i++) {
            if (i < source.length() && source.charAt(i) == '\n') {
                templateStartLine++;
            }
        }

        int contentStart = templateOpen + "<template>".length();
        // Skip leading newline after <template> tag
        if (contentStart < source.length() && source.charAt(contentStart) == '\n') {
            contentStart++;
            templateStartLine++;
        }

        int templateClose = source.indexOf("</template>", contentStart);
        if (templateClose == -1) {
            templateSource = source.substring(contentStart);
        } else {
            templateSource = source.substring(contentStart, templateClose);
        }

        // Trim trailing newline before </template>
        if (templateSource.endsWith("\n")) {
            templateSource = templateSource.substring(0, templateSource.length() - 1);
        }
    }

    /**
     * Try to extract template from @Template("""...""") annotation.
     * Returns true if found and extracted successfully.
     */
    private boolean splitFromAnnotation() {
        // Find @Template( with optional whitespace
        int atTemplate = source.indexOf("@Template(");
        if (atTemplate == -1) {
            atTemplate = source.indexOf("@Template (");
            if (atTemplate == -1) return false;
        }

        // Find the text block opening """
        int parenOpen = source.indexOf('(', atTemplate);
        int textBlockStart = source.indexOf("\"\"\"", parenOpen);
        if (textBlockStart == -1) return false;

        // Text block content starts after """ and the first newline
        int contentStart = textBlockStart + 3;
        if (contentStart < source.length() && source.charAt(contentStart) == '\n') {
            contentStart++;
        }

        // Find closing """ (scan for """ that's not escaped)
        int textBlockEnd = findClosingTextBlock(source, contentStart);
        if (textBlockEnd == -1) return false;

        // Find the closing ) after """
        int parenClose = source.indexOf(')', textBlockEnd + 3);
        if (parenClose == -1) return false;

        // Extract template content and strip common leading whitespace (text block semantics)
        templateSource = stripTextBlockIndent(source.substring(contentStart, textBlockEnd));

        // Trim trailing newline
        if (templateSource.endsWith("\n")) {
            templateSource = templateSource.substring(0, templateSource.length() - 1);
        }

        // Count lines before template content for error reporting
        templateStartLine = 1;
        for (int i = 0; i < contentStart; i++) {
            if (source.charAt(i) == '\n') templateStartLine++;
        }

        // Build javaSource: everything except the @Template(...) annotation
        javaSource = (source.substring(0, atTemplate) + source.substring(parenClose + 1)).trim();

        return true;
    }

    /**
     * Find the closing """ of a text block, starting from the content.
     */
    private static int findClosingTextBlock(String s, int from) {
        int i = from;
        while (i + 2 < s.length()) {
            if (s.charAt(i) == '"' && s.charAt(i + 1) == '"' && s.charAt(i + 2) == '"') {
                // Make sure it's not escaped
                int backslashes = 0;
                int j = i - 1;
                while (j >= 0 && s.charAt(j) == '\\') { backslashes++; j--; }
                if (backslashes % 2 == 0) {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }

    /**
     * Strip common leading whitespace from text block content (mimics Java text block behavior).
     */
    private static String stripTextBlockIndent(String content) {
        String[] lines = content.split("\n", -1);
        // Find minimum indent (ignoring blank lines)
        int minIndent = Integer.MAX_VALUE;
        for (String l : lines) {
            if (l.isBlank()) continue;
            int indent = 0;
            while (indent < l.length() && l.charAt(indent) == ' ') indent++;
            if (indent < minIndent) minIndent = indent;
        }
        if (minIndent == Integer.MAX_VALUE) minIndent = 0;

        // Strip common indent
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            if (lines[i].length() > minIndent) {
                sb.append(lines[i].substring(minIndent));
            } else if (!lines[i].isBlank()) {
                sb.append(lines[i]);
            }
        }
        return sb.toString();
    }

    // ========== BODY LEXING ==========

    private void lexBody() {
        if (pos >= templateSource.length()) return;

        if (lookingAt("{{")) {
            // Check for comment: {{-- ... --}}
            if (pos + 3 < templateSource.length()
                    && templateSource.charAt(pos + 2) == '-'
                    && templateSource.charAt(pos + 3) == '-') {
                lexComment();
                return;
            }
            // Check for whitespace-trimming open: {{-
            // (but not {{-- which is a comment)
            if (pos + 2 < templateSource.length()
                    && templateSource.charAt(pos + 2) == '-'
                    && (pos + 3 >= templateSource.length() || templateSource.charAt(pos + 3) != '-')) {
                lexTemplateExpression(true);
                return;
            }
            lexTemplateExpression(false);
        } else {
            lexHtml();
        }
    }

    /**
     * Consume a template comment {{-- ... --}} without emitting any tokens.
     */
    private void lexComment() {
        // Skip {{--
        advance(); advance(); advance(); advance();
        while (pos + 3 < templateSource.length()) {
            if (templateSource.charAt(pos) == '-'
                    && templateSource.charAt(pos + 1) == '-'
                    && templateSource.charAt(pos + 2) == '}'
                    && templateSource.charAt(pos + 3) == '}') {
                advance(); advance(); advance(); advance(); // skip --}}
                return;
            }
            advance();
        }
        // If we reach here, comment was never closed — consume remaining
        while (pos < templateSource.length()) advance();
    }

    private void lexHtml() {
        SourceLocation start = loc();
        StringBuilder sb = new StringBuilder();
        while (pos < templateSource.length() && !lookingAt("{{")) {
            sb.append(peek());
            advance();
        }
        if (!sb.isEmpty()) {
            String content = sb.toString();
            // Apply whitespace trimming if flagged from previous -}}
            if (trimNextHtmlLeading) {
                content = trimLeadingWhitespace(content);
                trimNextHtmlLeading = false;
            }
            if (!content.isEmpty()) {
                tokens.add(new Token(TokenType.HTML, content, start));
            }
        }
    }

    private void lexTemplateExpression(boolean trimLeft) {
        SourceLocation start = loc();
        advance(); // {
        advance(); // {
        if (trimLeft) {
            advance(); // - (the trim marker)
            // Retroactively trim trailing whitespace from the last HTML token
            trimLastHtmlTokenTrailing();
        }
        tokens.add(new Token(TokenType.EXPR_START, "{{", start));

        skipWhitespace();

        // Check for keywords
        if (pos >= templateSource.length() || lookingAt("}}") || lookingAt("-}}")) {
            emitExprEnd();
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
            case "include" -> {
                advance("include".length());
                tokens.add(new Token(TokenType.KEYWORD_INCLUDE, "include", start));
                skipWhitespace();
                // Include file name as string literal
                tokens.add(readStringLiteral());
                skipWhitespace();
                // Optional params: key=value
                lexKeyValueParams();
            }
            case "component" -> {
                advance("component".length());
                tokens.add(new Token(TokenType.KEYWORD_COMPONENT, "component", start));
                skipWhitespace();
                // Component name as string literal
                tokens.add(readStringLiteral());
                skipWhitespace();
                // Optional params: key=value
                lexKeyValueParams();
            }
            case "widget" -> {
                advance("widget".length());
                tokens.add(new Token(TokenType.KEYWORD_WIDGET, "widget", start));
                skipWhitespace();
                // Widget name as string literal
                tokens.add(readStringLiteral());
                skipWhitespace();
                // Optional params: key=value
                lexKeyValueParams();
            }
            case "fragment" -> {
                advance("fragment".length());
                tokens.add(new Token(TokenType.KEYWORD_FRAGMENT, "fragment", start));
                skipWhitespace();
                // Fragment name as string literal
                tokens.add(readStringLiteral());
            }
            case "content" -> {
                advance("content".length());
                tokens.add(new Token(TokenType.KEYWORD_CONTENT, "content", start));
            }
            case "set" -> {
                advance("set".length());
                tokens.add(new Token(TokenType.KEYWORD_SET, "set", start));
                skipWhitespace();
                lexExpressionTokensUntilClose();
            }
            case "switch" -> {
                advance("switch".length());
                tokens.add(new Token(TokenType.KEYWORD_SWITCH, "switch", start));
                skipWhitespace();
                lexExpressionTokensUntilClose();
            }
            case "case" -> {
                advance("case".length());
                tokens.add(new Token(TokenType.KEYWORD_CASE, "case", start));
                skipWhitespace();
                lexExpressionTokensUntilClose();
            }
            case "default" -> {
                advance("default".length());
                tokens.add(new Token(TokenType.KEYWORD_DEFAULT, "default", start));
            }
            case "verbatim" -> {
                advance("verbatim".length());
                // Remove the EXPR_START token that was already emitted (verbatim is handled entirely in lexer)
                if (!tokens.isEmpty() && tokens.getLast().type() == TokenType.EXPR_START) {
                    tokens.removeLast();
                }
                // Consume the closing }}
                skipWhitespace();
                if (lookingAt("-}}")) {
                    advance(); advance(); advance();
                    trimNextHtmlLeading = true;
                } else if (lookingAt("}}")) {
                    advance(); advance();
                } else {
                    throw error("Expected '}}' after verbatim");
                }
                // Now consume everything until {{ end }} as raw HTML
                lexVerbatimContent();
                return; // lexVerbatimContent handles everything including {{ end }}
            }
            case "slot" -> {
                advance("slot".length());
                tokens.add(new Token(TokenType.KEYWORD_SLOT, "slot", start));
                skipWhitespace();
                tokens.add(readStringLiteral());
            }
            case "block" -> {
                advance("block".length());
                tokens.add(new Token(TokenType.KEYWORD_BLOCK, "block", start));
                skipWhitespace();
                tokens.add(readStringLiteral());
            }
            case "stack" -> {
                advance("stack".length());
                tokens.add(new Token(TokenType.KEYWORD_STACK, "stack", start));
                skipWhitespace();
                tokens.add(readStringLiteral());
            }
            case "push" -> {
                advance("push".length());
                tokens.add(new Token(TokenType.KEYWORD_PUSH, "push", start));
                skipWhitespace();
                tokens.add(readStringLiteral());
            }
            default -> {
                // Regular expression output
                lexExpressionTokensUntilClose();
            }
        }

        // Consume closing }} (possibly with whitespace-trimming -}})
        emitExprEnd();
    }

    /**
     * Consume everything until {{ end }} as a single HTML token (verbatim block).
     * No tokens are emitted for the verbatim/end markers themselves.
     */
    private void lexVerbatimContent() {
        SourceLocation start = loc();
        StringBuilder sb = new StringBuilder();
        while (pos < templateSource.length()) {
            // Look for {{ end }}
            if (lookingAt("{{")) {
                int saved = pos;
                int savedLine = line;
                int savedCol = col;
                advance(); advance(); // skip {{
                // Check for optional trim marker
                if (pos < templateSource.length() && peek() == '-') advance();
                skipWhitespace();
                if (peekIdentifier().equals("end")) {
                    advance("end".length());
                    skipWhitespace();
                    // Check for -}} or }}
                    if (lookingAt("-}}")) {
                        advance(); advance(); advance();
                        trimNextHtmlLeading = true;
                    } else if (lookingAt("}}")) {
                        advance(); advance();
                    }
                    // Emit verbatim content as HTML
                    if (!sb.isEmpty()) {
                        tokens.add(new Token(TokenType.HTML, sb.toString(), start));
                    }
                    return;
                }
                // Not {{ end }}, restore and continue
                pos = saved;
                line = savedLine;
                col = savedCol;
            }
            sb.append(peek());
            advance();
        }
        // Unterminated verbatim — emit what we have
        if (!sb.isEmpty()) {
            tokens.add(new Token(TokenType.HTML, sb.toString(), start));
        }
    }

    /**
     * Emit EXPR_END token, handling whitespace-trimming -}}.
     */
    private void emitExprEnd() {
        skipWhitespace();
        if (lookingAt("-}}")) {
            SourceLocation endLoc = loc();
            advance(); // -
            advance(); // }
            advance(); // }
            tokens.add(new Token(TokenType.EXPR_END, "}}", endLoc));
            trimNextHtmlLeading = true;
        } else if (lookingAt("}}")) {
            SourceLocation endLoc = loc();
            advance();
            advance();
            tokens.add(new Token(TokenType.EXPR_END, "}}", endLoc));
        } else {
            throw error("Expected '}}' to close template expression");
        }
    }

    /**
     * Trim trailing whitespace (including newline) from the last HTML token.
     */
    private void trimLastHtmlTokenTrailing() {
        for (int i = tokens.size() - 1; i >= 0; i--) {
            Token t = tokens.get(i);
            if (t.type() == TokenType.HTML) {
                String trimmed = trimTrailingWhitespace(t.value());
                if (trimmed.isEmpty()) {
                    tokens.remove(i);
                } else {
                    tokens.set(i, new Token(TokenType.HTML, trimmed, t.location()));
                }
                break;
            } else if (t.type() != TokenType.EXPR_END && t.type() != TokenType.EXPR_START) {
                break; // only trim adjacent HTML
            }
        }
    }

    private static String trimTrailingWhitespace(String s) {
        int end = s.length();
        while (end > 0 && isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }

    private static String trimLeadingWhitespace(String s) {
        int start = 0;
        while (start < s.length() && isWhitespace(s.charAt(start))) {
            start++;
        }
        return s.substring(start);
    }

    private void lexKeyValueParams() {
        while (pos < templateSource.length() && !lookingAt("}}") && !lookingAt("-}}")) {
            skipWhitespace();
            if (lookingAt("}}") || lookingAt("-}}")) break;

            // key=value
            SourceLocation keyLoc = loc();
            String key = readIdentifier();
            tokens.add(new Token(TokenType.IDENTIFIER, key, keyLoc));

            skipWhitespace();
            if (pos < templateSource.length() && peek() == '=') {
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
        while (pos < templateSource.length() && !lookingAt("}}") && !lookingAt("-}}") && !isWhitespace(peek())) {
            lexOneExpressionToken();
        }
    }

    private void lexExpressionTokensUntilClose() {
        while (pos < templateSource.length() && !lookingAt("}}") && !lookingAt("-}}")) {
            skipWhitespace();
            if (lookingAt("}}") || lookingAt("-}}")) break;
            lexOneExpressionToken();
        }
    }

    private void lexOneExpressionToken() {
        skipWhitespace();
        if (pos >= templateSource.length() || lookingAt("}}") || lookingAt("-}}")) return;

        SourceLocation tokLoc = loc();
        char c = peek();

        switch (c) {
            case '.' -> {
                advance();
                tokens.add(new Token(TokenType.DOT, ".", tokLoc));
            }
            case '?' -> {
                advance();
                if (pos < templateSource.length() && peek() == '.') {
                    advance();
                    tokens.add(new Token(TokenType.NULL_SAFE_DOT, "?.", tokLoc));
                } else if (pos < templateSource.length() && peek() == '?') {
                    advance();
                    tokens.add(new Token(TokenType.NULL_COALESCE, "??", tokLoc));
                } else {
                    tokens.add(new Token(TokenType.QUESTION, "?", tokLoc));
                }
            }
            case ':' -> {
                advance();
                tokens.add(new Token(TokenType.COLON, ":", tokLoc));
            }
            case '(' -> {
                advance();
                tokens.add(new Token(TokenType.LPAREN, "(", tokLoc));
            }
            case ')' -> {
                advance();
                tokens.add(new Token(TokenType.RPAREN, ")", tokLoc));
            }
            case '[' -> {
                advance();
                tokens.add(new Token(TokenType.LBRACKET, "[", tokLoc));
            }
            case ']' -> {
                advance();
                tokens.add(new Token(TokenType.RBRACKET, "]", tokLoc));
            }
            case ',' -> {
                advance();
                tokens.add(new Token(TokenType.COMMA, ",", tokLoc));
            }
            case '=' -> {
                advance();
                if (pos < templateSource.length() && peek() == '=') {
                    advance();
                    tokens.add(new Token(TokenType.EQ, "==", tokLoc));
                } else {
                    tokens.add(new Token(TokenType.EQUALS_SIGN, "=", tokLoc));
                }
            }
            case '!' -> {
                advance();
                if (pos < templateSource.length() && peek() == '=') {
                    advance();
                    tokens.add(new Token(TokenType.NEQ, "!=", tokLoc));
                } else {
                    tokens.add(new Token(TokenType.NOT, "!", tokLoc));
                }
            }
            case '<' -> {
                advance();
                if (pos < templateSource.length() && peek() == '=') {
                    advance();
                    tokens.add(new Token(TokenType.LTE, "<=", tokLoc));
                } else {
                    tokens.add(new Token(TokenType.LT, "<", tokLoc));
                }
            }
            case '>' -> {
                advance();
                if (pos < templateSource.length() && peek() == '=') {
                    advance();
                    tokens.add(new Token(TokenType.GTE, ">=", tokLoc));
                } else {
                    tokens.add(new Token(TokenType.GT, ">", tokLoc));
                }
            }
            case '&' -> {
                advance();
                if (pos < templateSource.length() && peek() == '&') {
                    advance();
                    tokens.add(new Token(TokenType.AND, "&&", tokLoc));
                } else {
                    throw error("Expected '&&', got single '&'", tokLoc);
                }
            }
            case '|' -> {
                advance();
                if (pos < templateSource.length() && peek() == '|') {
                    advance();
                    tokens.add(new Token(TokenType.OR, "||", tokLoc));
                } else {
                    tokens.add(new Token(TokenType.PIPE, "|", tokLoc));
                }
            }
            case '+' -> {
                advance();
                tokens.add(new Token(TokenType.PLUS, "+", tokLoc));
            }
            case '-' -> {
                advance();
                tokens.add(new Token(TokenType.MINUS, "-", tokLoc));
            }
            case '*' -> {
                advance();
                tokens.add(new Token(TokenType.STAR, "*", tokLoc));
            }
            case '/' -> {
                advance();
                tokens.add(new Token(TokenType.SLASH, "/", tokLoc));
            }
            case '%' -> {
                advance();
                tokens.add(new Token(TokenType.PERCENT, "%", tokLoc));
            }
            case '~' -> {
                advance();
                tokens.add(new Token(TokenType.TILDE, "~", tokLoc));
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
        while (pos < templateSource.length() && peek() != '"') {
            if (peek() == '\\') {
                advance();
                if (pos >= templateSource.length()) throw error("Unterminated string literal", start);
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
        if (pos >= templateSource.length()) {
            throw error("Unterminated string literal", start);
        }
        advance(); // skip closing "
        return new Token(TokenType.STRING_LITERAL, sb.toString(), start);
    }

    private Token readNumber() {
        SourceLocation start = loc();
        StringBuilder sb = new StringBuilder();
        while (pos < templateSource.length() && (Character.isDigit(peek()) || peek() == '.')) {
            sb.append(peek());
            advance();
        }
        return new Token(TokenType.NUMBER, sb.toString(), start);
    }

    private String readIdentifier() {
        StringBuilder sb = new StringBuilder();
        while (pos < templateSource.length() && Character.isJavaIdentifierPart(peek())) {
            sb.append(peek());
            advance();
        }
        if (sb.isEmpty()) {
            throw error("Expected identifier");
        }
        return sb.toString();
    }

    private String peekIdentifier() {
        int saved = pos;
        StringBuilder sb = new StringBuilder();
        while (saved < templateSource.length() && Character.isJavaIdentifierPart(templateSource.charAt(saved))) {
            sb.append(templateSource.charAt(saved));
            saved++;
        }
        return sb.toString();
    }

    private char peek() {
        return templateSource.charAt(pos);
    }

    private boolean lookingAt(String s) {
        return templateSource.startsWith(s, pos);
    }

    private void advance() {
        if (pos < templateSource.length()) {
            if (templateSource.charAt(pos) == '\n') {
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

    private void skipWhitespace() {
        while (pos < templateSource.length() && isWhitespace(peek()) && peek() != '\n') {
            advance();
        }
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
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
