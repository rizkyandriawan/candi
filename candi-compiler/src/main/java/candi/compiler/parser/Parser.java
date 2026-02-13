package candi.compiler.parser;

import candi.compiler.CompileError;
import candi.compiler.SourceLocation;
import candi.compiler.ast.*;
import candi.compiler.expr.Expression;
import candi.compiler.expr.ExpressionParser;
import candi.compiler.lexer.Token;
import candi.compiler.lexer.TokenType;

import java.util.*;

/**
 * Recursive descent parser for the template section of a .page.html file (v2).
 * Parses template body only — no header directives.
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

    /**
     * Parse the template tokens into a BodyNode.
     */
    public BodyNode parseBody() {
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
     * Parse body content until we hit {{ end }} or {{ else }} or {{ case }} or {{ default }}.
     * Used for if/for/switch block bodies.
     */
    private BodyNode parseBodyUntilEndOrElse() {
        List<Node> children = new ArrayList<>();
        SourceLocation loc = isAtEnd() ? new SourceLocation(fileName, 1, 1) : peek().location();

        while (!isAtEnd()) {
            if (check(TokenType.HTML)) {
                Token html = consume();
                children.add(new HtmlNode(html.value(), html.location()));
            } else if (check(TokenType.EXPR_START)) {
                // Peek ahead to check for end/else/case/default keywords
                if (isExprKeyword(TokenType.KEYWORD_END)
                        || isExprKeyword(TokenType.KEYWORD_ELSE)
                        || isExprKeyword(TokenType.KEYWORD_CASE)
                        || isExprKeyword(TokenType.KEYWORD_DEFAULT)) {
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
            case KEYWORD_INCLUDE -> parseInclude(start);
            case KEYWORD_COMPONENT -> parseComponentCall(start);
            case KEYWORD_WIDGET -> parseWidgetCall(start);
            case KEYWORD_FRAGMENT -> parseFragmentBlock(start);
            case KEYWORD_CONTENT -> parseContent(start);
            case KEYWORD_SET -> parseSet(start);
            case KEYWORD_SWITCH -> parseSwitchBlock(start);
            case KEYWORD_SLOT -> parseSlotBlock(start);
            case KEYWORD_BLOCK -> parseBlockBlock(start);
            case KEYWORD_STACK -> parseStack(start);
            case KEYWORD_PUSH -> parsePushBlock(start);
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

    private FragmentNode parseFragmentBlock(SourceLocation start) {
        consume(); // fragment keyword
        Token name = expect(TokenType.STRING_LITERAL, "fragment name");
        expect(TokenType.EXPR_END, "'}}'");

        BodyNode body = parseBodyUntilEndOrElse();

        // Consume {{ end }}
        expect(TokenType.EXPR_START, "'{{'");
        expect(TokenType.KEYWORD_END, "'end'");
        expect(TokenType.EXPR_END, "'}}'");

        return new FragmentNode(name.value(), body, start);
    }

    private RawExpressionOutputNode parseRawExpression(SourceLocation start) {
        consume(); // raw keyword
        Expression expr = parseExpression();
        expect(TokenType.EXPR_END, "'}}'");
        return new RawExpressionOutputNode(expr, start);
    }

    private IncludeNode parseInclude(SourceLocation start) {
        consume(); // include keyword
        Token name = expect(TokenType.STRING_LITERAL, "include file name");

        Map<String, Expression> params = parseKeyValueParams();

        expect(TokenType.EXPR_END, "'}}'");
        return new IncludeNode(name.value(), params, start);
    }

    private ComponentCallNode parseComponentCall(SourceLocation start) {
        consume(); // component keyword
        Token name = expect(TokenType.STRING_LITERAL, "component name");

        Map<String, Expression> params = parseKeyValueParams();

        expect(TokenType.EXPR_END, "'}}'");
        return new ComponentCallNode(name.value(), params, start);
    }

    private ComponentCallNode parseWidgetCall(SourceLocation start) {
        consume(); // widget keyword
        Token name = expect(TokenType.STRING_LITERAL, "widget name");

        Map<String, Expression> params = parseKeyValueParams();

        expect(TokenType.EXPR_END, "'}}'");
        return new ComponentCallNode(name.value(), params, start);
    }

    /**
     * Parse key=value parameter pairs until EXPR_END.
     * Each value is a single expression token (string literal, number, identifier, etc.).
     */
    private Map<String, Expression> parseKeyValueParams() {
        Map<String, Expression> params = new LinkedHashMap<>();
        while (!check(TokenType.EXPR_END) && !isAtEnd()) {
            Token key = expect(TokenType.IDENTIFIER, "parameter name");
            expect(TokenType.EQUALS_SIGN, "'='");
            Expression value = parseSingleExpression();
            params.put(key.value(), value);
        }
        return params;
    }

    /**
     * Parse a single value expression for a parameter (not greedy).
     * Handles: string literals, numbers, booleans, identifiers, and dotted property access.
     */
    private Expression parseSingleExpression() {
        Token t = peek();
        if (t == null) throw error("Expected expression value", new SourceLocation(fileName, 0, 0));

        if (check(TokenType.STRING_LITERAL)) {
            Token tok = consume();
            return new Expression.StringLiteral(tok.value(), tok.location());
        }
        if (check(TokenType.NUMBER)) {
            Token tok = consume();
            return new Expression.NumberLiteral(tok.value(), tok.location());
        }
        if (check(TokenType.TRUE) || check(TokenType.FALSE)) {
            Token tok = consume();
            return new Expression.BooleanLiteral("true".equals(tok.value()), tok.location());
        }
        if (check(TokenType.IDENTIFIER)) {
            // Could be simple identifier or dotted access (e.g. post.title)
            List<Token> exprTokens = new ArrayList<>();
            exprTokens.add(consume());
            while ((check(TokenType.DOT) || check(TokenType.NULL_SAFE_DOT)) && !isAtEnd()) {
                exprTokens.add(consume()); // dot
                exprTokens.add(expect(TokenType.IDENTIFIER, "property name")); // property
                // Handle method calls
                if (check(TokenType.LPAREN)) {
                    exprTokens.add(consume()); // (
                    while (!check(TokenType.RPAREN) && !isAtEnd()) {
                        exprTokens.add(consume());
                    }
                    exprTokens.add(expect(TokenType.RPAREN, "')'"));
                }
            }
            ExpressionParser ep = new ExpressionParser(exprTokens);
            return ep.parse();
        }

        throw error("Unexpected token in parameter value: " + t.type(), t.location());
    }

    private ContentNode parseContent(SourceLocation start) {
        consume(); // content keyword
        expect(TokenType.EXPR_END, "'}}'");
        return new ContentNode(start);
    }

    private SetNode parseSet(SourceLocation start) {
        consume(); // set keyword
        Token name = expect(TokenType.IDENTIFIER, "variable name");
        expect(TokenType.EQUALS_SIGN, "'='");
        Expression value = parseExpression();
        expect(TokenType.EXPR_END, "'}}'");
        return new SetNode(name.value(), value, start);
    }

    private SwitchNode parseSwitchBlock(SourceLocation start) {
        consume(); // switch keyword
        Expression subject = parseExpression();
        expect(TokenType.EXPR_END, "'}}'");

        List<SwitchNode.CaseBranch> cases = new ArrayList<>();
        BodyNode defaultBody = null;

        // Parse case/default branches until end
        while (!isAtEnd()) {
            // Skip HTML between switch and first case (usually whitespace)
            if (check(TokenType.HTML)) {
                consume();
                continue;
            }

            if (isExprKeyword(TokenType.KEYWORD_CASE)) {
                expect(TokenType.EXPR_START, "'{{'");
                consume(); // case keyword
                Expression caseValue = parseExpression();
                expect(TokenType.EXPR_END, "'}}'");
                BodyNode caseBody = parseBodyUntilEndOrElse();
                cases.add(new SwitchNode.CaseBranch(caseValue, caseBody));
            } else if (isExprKeyword(TokenType.KEYWORD_DEFAULT)) {
                expect(TokenType.EXPR_START, "'{{'");
                consume(); // default keyword
                expect(TokenType.EXPR_END, "'}}'");
                defaultBody = parseBodyUntilEndOrElse();
            } else if (isExprKeyword(TokenType.KEYWORD_END)) {
                break;
            } else {
                break;
            }
        }

        // Consume {{ end }}
        expect(TokenType.EXPR_START, "'{{'");
        expect(TokenType.KEYWORD_END, "'end'");
        expect(TokenType.EXPR_END, "'}}'");

        return new SwitchNode(subject, cases, defaultBody, start);
    }

    private SlotNode parseSlotBlock(SourceLocation start) {
        consume(); // slot keyword
        Token name = expect(TokenType.STRING_LITERAL, "slot name");
        expect(TokenType.EXPR_END, "'}}'");

        // Check if this is a self-closing slot (next is {{ end }}) or has default content
        BodyNode defaultContent = parseBodyUntilEndOrElse();

        // Consume {{ end }}
        expect(TokenType.EXPR_START, "'{{'");
        expect(TokenType.KEYWORD_END, "'end'");
        expect(TokenType.EXPR_END, "'}}'");

        return new SlotNode(name.value(), defaultContent, start);
    }

    private BlockNode parseBlockBlock(SourceLocation start) {
        consume(); // block keyword
        Token name = expect(TokenType.STRING_LITERAL, "block name");
        expect(TokenType.EXPR_END, "'}}'");

        BodyNode body = parseBodyUntilEndOrElse();

        // Consume {{ end }}
        expect(TokenType.EXPR_START, "'{{'");
        expect(TokenType.KEYWORD_END, "'end'");
        expect(TokenType.EXPR_END, "'}}'");

        return new BlockNode(name.value(), body, start);
    }

    private StackNode parseStack(SourceLocation start) {
        consume(); // stack keyword
        Token name = expect(TokenType.STRING_LITERAL, "stack name");
        expect(TokenType.EXPR_END, "'}}'");
        return new StackNode(name.value(), start);
    }

    private PushNode parsePushBlock(SourceLocation start) {
        consume(); // push keyword
        Token name = expect(TokenType.STRING_LITERAL, "stack name");
        expect(TokenType.EXPR_END, "'}}'");

        BodyNode body = parseBodyUntilEndOrElse();

        // Consume {{ end }}
        expect(TokenType.EXPR_START, "'{{'");
        expect(TokenType.KEYWORD_END, "'end'");
        expect(TokenType.EXPR_END, "'}}'");

        return new PushNode(name.value(), body, start);
    }

    private ExpressionOutputNode parseExpressionOutput(SourceLocation start) {
        Expression expr = parseExpression();
        expect(TokenType.EXPR_END, "'}}'");
        return new ExpressionOutputNode(expr, start);
    }

    /**
     * Parse expression from the remaining tokens (until EXPR_END).
     */
    private Expression parseExpression() {
        List<Token> exprTokens = new ArrayList<>();
        while (!check(TokenType.EXPR_END) && !isAtEnd()) {
            TokenType t = peek().type();
            if (t == TokenType.KEYWORD_END || t == TokenType.KEYWORD_ELSE
                    || t == TokenType.KEYWORD_CASE || t == TokenType.KEYWORD_DEFAULT) break;
            exprTokens.add(consume());
        }

        if (exprTokens.isEmpty()) {
            throw error("Expected expression", peek() != null ? peek().location()
                    : new SourceLocation(fileName, 0, 0));
        }

        ExpressionParser ep = new ExpressionParser(exprTokens);
        return ep.parse();
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
