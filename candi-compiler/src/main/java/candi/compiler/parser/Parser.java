package candi.compiler.parser;

import candi.compiler.CompileError;
import candi.compiler.SourceLocation;
import candi.compiler.ast.*;
import candi.compiler.expr.Expression;
import candi.compiler.expr.ExpressionParser;
import candi.compiler.lexer.Lexer;
import candi.compiler.lexer.Token;
import candi.compiler.lexer.TokenType;

import java.util.*;

/**
 * Recursive descent parser that produces the page AST from tokens.
 */
public class Parser {

    private final List<Token> tokens;
    private final String fileName;
    private int pos;

    public Parser(List<Token> tokens, String fileName) {
        this.tokens = tokens;
        this.fileName = fileName;
        this.pos = 0;
    }

    public PageNode parse() {
        String path = null;
        List<InjectNode> injects = new ArrayList<>();
        InitNode init = null;
        List<ActionNode> actions = new ArrayList<>();
        List<FragmentDefNode> fragments = new ArrayList<>();
        LayoutDirectiveNode layout = null;
        List<SlotFillNode> slotFills = new ArrayList<>();

        SourceLocation pageLoc = peek().location();

        // Parse header directives
        while (!isAtEnd() && !check(TokenType.HTML) && !check(TokenType.EXPR_START)) {
            Token t = peek();
            switch (t.type()) {
                case PAGE -> {
                    consume();
                    Token pathToken = expect(TokenType.STRING_LITERAL, "page path");
                    path = pathToken.value();
                }
                case INJECT -> {
                    SourceLocation loc = consume().location();
                    Token type = expect(TokenType.IDENTIFIER, "type name");
                    Token name = expect(TokenType.IDENTIFIER, "variable name");
                    injects.add(new InjectNode(type.value(), name.value(), loc));
                }
                case INIT -> {
                    SourceLocation loc = consume().location();
                    Token code = expect(TokenType.CODE_BLOCK, "code block");
                    init = new InitNode(code.value(), loc);
                }
                case ACTION -> {
                    SourceLocation loc = consume().location();
                    Token method = expect(TokenType.HTTP_METHOD, "HTTP method");
                    Token code = expect(TokenType.CODE_BLOCK, "code block");
                    actions.add(new ActionNode(method.value(), code.value(), loc));
                }
                case FRAGMENT_DEF -> {
                    SourceLocation loc = consume().location();
                    Token name = expect(TokenType.STRING_LITERAL, "fragment name");
                    Token bodyCode = expect(TokenType.CODE_BLOCK, "fragment body");
                    // Re-lex the fragment body as template content
                    BodyNode body = parseFragmentBody(bodyCode.value(), loc);
                    fragments.add(new FragmentDefNode(name.value(), body, loc));
                }
                case LAYOUT -> {
                    SourceLocation loc = consume().location();
                    Token name = expect(TokenType.STRING_LITERAL, "layout name");
                    layout = new LayoutDirectiveNode(name.value(), loc);
                }
                case SLOT_FILL -> {
                    SourceLocation loc = consume().location();
                    Token name = expect(TokenType.IDENTIFIER, "slot name");
                    Token bodyCode = expect(TokenType.CODE_BLOCK, "slot body");
                    BodyNode body = parseFragmentBody(bodyCode.value(), loc);
                    slotFills.add(new SlotFillNode(name.value(), body, loc));
                }
                case EOF -> {
                    // Page with no body — just header directives
                    break;
                }
                default -> throw error("Unexpected token " + t.type() + " in header", t.location());
            }
            if (check(TokenType.EOF)) break;
        }

        // Parse body
        BodyNode body = parseBody();

        return new PageNode(path, injects, init, actions, fragments, layout, slotFills, body, pageLoc);
    }

    private BodyNode parseBody() {
        List<Node> children = new ArrayList<>();
        SourceLocation loc = isAtEnd() ? new SourceLocation(fileName, 1, 1) : peek().location();

        while (!isAtEnd()) {
            if (check(TokenType.HTML)) {
                Token html = consume();
                children.add(new HtmlNode(html.value(), html.location()));
            } else if (check(TokenType.EXPR_START)) {
                children.add(parseTemplateExpression());
            } else if (check(TokenType.EOF)) {
                break;
            } else {
                throw error("Unexpected token " + peek().type() + " in body", peek().location());
            }
        }

        return new BodyNode(children, loc);
    }

    /**
     * Parse body content until we hit {{ end }} or {{ else }}.
     * Used for if/for block bodies.
     */
    private BodyNode parseBodyUntilEndOrElse() {
        List<Node> children = new ArrayList<>();
        SourceLocation loc = isAtEnd() ? new SourceLocation(fileName, 1, 1) : peek().location();

        while (!isAtEnd()) {
            if (check(TokenType.HTML)) {
                Token html = consume();
                children.add(new HtmlNode(html.value(), html.location()));
            } else if (check(TokenType.EXPR_START)) {
                // Peek ahead to check for end/else keywords
                if (isExprKeyword(TokenType.KEYWORD_END) || isExprKeyword(TokenType.KEYWORD_ELSE)) {
                    break;
                }
                children.add(parseTemplateExpression());
            } else if (check(TokenType.EOF)) {
                break;
            } else {
                throw error("Unexpected token " + peek().type() + " in body", peek().location());
            }
        }

        return new BodyNode(children, loc);
    }

    /**
     * Check if the next template expression starts with the given keyword.
     * Looks at {{ KEYWORD pattern without consuming tokens.
     */
    private boolean isExprKeyword(TokenType keyword) {
        if (pos + 1 >= tokens.size()) return false;
        return check(TokenType.EXPR_START) && tokens.get(pos + 1).type() == keyword;
    }

    private Node parseTemplateExpression() {
        SourceLocation start = expect(TokenType.EXPR_START, "'{{'").location();

        Token next = peek();
        if (next == null) throw error("Unexpected end of input after '{{'", start);

        return switch (next.type()) {
            case KEYWORD_IF -> parseIfBlock(start);
            case KEYWORD_FOR -> parseForBlock(start);
            case KEYWORD_RAW -> parseRawExpression(start);
            case KEYWORD_FRAGMENT -> parseFragmentCall(start);
            case KEYWORD_COMPONENT -> parseComponentCall(start);
            case KEYWORD_SLOT -> parseSlotRender(start);
            default -> parseExpressionOutput(start);
        };
    }

    private IfNode parseIfBlock(SourceLocation start) {
        consume(); // if keyword
        Expression condition = parseExpression();
        expect(TokenType.EXPR_END, "'}}'");

        BodyNode thenBody = parseBodyUntilEndOrElse();

        BodyNode elseBody = null;
        boolean elseIfConsumedEnd = false;
        if (isExprKeyword(TokenType.KEYWORD_ELSE)) {
            expect(TokenType.EXPR_START, "'{{'");
            consume(); // else keyword

            // Check for else if
            if (check(TokenType.KEYWORD_IF)) {
                // {{ else if cond }} — desugar to else { if cond { ... } }
                // The nested parseIfBlock will consume {{ end }} for the whole chain
                IfNode elseIf = parseIfBlock(peek().location());
                elseBody = new BodyNode(List.of(elseIf), elseIf.location());
                elseIfConsumedEnd = true;
            } else {
                expect(TokenType.EXPR_END, "'}}'");
                elseBody = parseBodyUntilEndOrElse();
            }
        }

        // Consume {{ end }} — but not if else-if already consumed it
        if (!elseIfConsumedEnd) {
            expect(TokenType.EXPR_START, "'{{'");
            expect(TokenType.KEYWORD_END, "'end'");
            expect(TokenType.EXPR_END, "'}}'");
        }

        return new IfNode(condition, thenBody, elseBody, start);
    }

    private ForNode parseForBlock(SourceLocation start) {
        consume(); // for keyword
        Token varName = expect(TokenType.IDENTIFIER, "loop variable name");
        expect(TokenType.KEYWORD_IN, "'in'");
        Expression collection = parseExpression();
        expect(TokenType.EXPR_END, "'}}'");

        BodyNode body = parseBodyUntilEndOrElse();

        // Consume {{ end }}
        expect(TokenType.EXPR_START, "'{{'");
        expect(TokenType.KEYWORD_END, "'end'");
        expect(TokenType.EXPR_END, "'}}'");

        return new ForNode(varName.value(), collection, body, start);
    }

    private RawExpressionOutputNode parseRawExpression(SourceLocation start) {
        consume(); // raw keyword
        Expression expr = parseExpression();
        expect(TokenType.EXPR_END, "'}}'");
        return new RawExpressionOutputNode(expr, start);
    }

    private FragmentCallNode parseFragmentCall(SourceLocation start) {
        consume(); // fragment keyword
        Token name = expect(TokenType.STRING_LITERAL, "fragment name");
        expect(TokenType.EXPR_END, "'}}'");
        return new FragmentCallNode(name.value(), start);
    }

    private ComponentCallNode parseComponentCall(SourceLocation start) {
        consume(); // component keyword
        Token name = expect(TokenType.STRING_LITERAL, "component name");

        // Parse params: key=value pairs
        Map<String, Expression> params = new LinkedHashMap<>();
        while (!check(TokenType.EXPR_END) && !isAtEnd()) {
            Token key = expect(TokenType.IDENTIFIER, "parameter name");
            expect(TokenType.EQUALS_SIGN, "'='");
            Expression value = parseExpression();
            params.put(key.value(), value);
        }

        expect(TokenType.EXPR_END, "'}}'");
        return new ComponentCallNode(name.value(), params, start);
    }

    private SlotRenderNode parseSlotRender(SourceLocation start) {
        consume(); // slot keyword
        String slotName = "default";
        if (check(TokenType.IDENTIFIER)) {
            slotName = consume().value();
        }
        expect(TokenType.EXPR_END, "'}}'");
        return new SlotRenderNode(slotName, start);
    }

    private ExpressionOutputNode parseExpressionOutput(SourceLocation start) {
        Expression expr = parseExpression();
        expect(TokenType.EXPR_END, "'}}'");
        return new ExpressionOutputNode(expr, start);
    }

    /**
     * Parse expression from the remaining tokens (until EXPR_END).
     * Collects expression tokens and delegates to ExpressionParser.
     */
    private Expression parseExpression() {
        List<Token> exprTokens = new ArrayList<>();
        while (!check(TokenType.EXPR_END) && !isAtEnd()) {
            // Stop at tokens that aren't part of expressions
            TokenType t = peek().type();
            if (t == TokenType.KEYWORD_END || t == TokenType.KEYWORD_ELSE) break;
            exprTokens.add(consume());
        }

        if (exprTokens.isEmpty()) {
            throw error("Expected expression", peek() != null ? peek().location()
                    : new SourceLocation(fileName, 0, 0));
        }

        ExpressionParser ep = new ExpressionParser(exprTokens);
        return ep.parse();
    }

    /**
     * Re-lex fragment/slot body content as template HTML.
     * The content was captured as a brace-matched code block but contains HTML + {{ }}.
     */
    private BodyNode parseFragmentBody(String content, SourceLocation baseLoc) {
        // Create a mini-lexer that starts in body mode
        Lexer bodyLexer = new Lexer(content, fileName);
        // Force body mode by just letting it lex — fragment content has no @directives
        List<Token> bodyTokens = bodyLexer.tokenize();

        Parser bodyParser = new Parser(bodyTokens, fileName);
        return bodyParser.parseBody();
    }

    // ========== Token helpers ==========

    private boolean check(TokenType type) {
        Token t = peek();
        return t != null && t.type() == type;
    }

    private Token peek() {
        if (pos >= tokens.size()) return null;
        return tokens.get(pos);
    }

    private Token consume() {
        return tokens.get(pos++);
    }

    private Token expect(TokenType type, String description) {
        Token t = peek();
        if (t == null || t.type() != type) {
            SourceLocation loc = t != null ? t.location()
                    : (!tokens.isEmpty() ? tokens.getLast().location() : new SourceLocation(fileName, 0, 0));
            throw error("Expected " + description + " but got " +
                    (t != null ? t.type() + "('" + t.value() + "')" : "end of input"), loc);
        }
        return consume();
    }

    private boolean isAtEnd() {
        return pos >= tokens.size() || tokens.get(pos).type() == TokenType.EOF;
    }

    private CompileError error(String message, SourceLocation loc) {
        return new CompileError(message, loc);
    }

    private CompileError error(String message) {
        SourceLocation loc = peek() != null ? peek().location()
                : new SourceLocation(fileName, 0, 0);
        return new CompileError(message, loc);
    }
}
