package candi.compiler.lexer;

public enum TokenType {
    // Body content
    HTML,           // raw HTML text

    // Template expressions (inside {{ }})
    EXPR_START,     // {{
    EXPR_END,       // }}
    KEYWORD_IF,     // if
    KEYWORD_ELSE,   // else
    KEYWORD_END,    // end
    KEYWORD_FOR,    // for
    KEYWORD_IN,     // in
    KEYWORD_RAW,    // raw
    KEYWORD_INCLUDE,   // include
    KEYWORD_COMPONENT, // component (legacy, kept for backward compat)
    KEYWORD_WIDGET,    // widget
    KEYWORD_CONTENT,   // content (layout placeholder)

    // Literals
    STRING_LITERAL, // "..."
    IDENTIFIER,     // variable/type names
    NUMBER,         // numeric literal

    // Expression tokens
    DOT,            // .
    NULL_SAFE_DOT,  // ?.
    LPAREN,         // (
    RPAREN,         // )
    COMMA,          // ,
    EQUALS_SIGN,    // = (for param=val)
    EQ,             // ==
    NEQ,            // !=
    LT,             // <
    GT,             // >
    LTE,            // <=
    GTE,            // >=
    AND,            // &&
    OR,             // ||
    NOT,            // !
    TRUE,           // true
    FALSE,          // false

    EOF
}
