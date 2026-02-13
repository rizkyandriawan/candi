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
    KEYWORD_FRAGMENT,  // fragment (AJAX fragment block)
    KEYWORD_SET,       // set (variable assignment)
    KEYWORD_SWITCH,    // switch
    KEYWORD_CASE,      // case
    KEYWORD_DEFAULT,   // default
    KEYWORD_VERBATIM,  // verbatim (raw block, no parsing)
    KEYWORD_SLOT,      // slot (named slot in layout)
    KEYWORD_BLOCK,     // block (slot content in page)
    KEYWORD_STACK,     // stack (render stacked content)
    KEYWORD_PUSH,      // push (push content to stack)

    // Literals
    STRING_LITERAL, // "..."
    IDENTIFIER,     // variable/type names
    NUMBER,         // numeric literal

    // Expression tokens
    DOT,            // .
    NULL_SAFE_DOT,  // ?.
    LPAREN,         // (
    RPAREN,         // )
    LBRACKET,       // [
    RBRACKET,       // ]
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

    // Ternary / null coalescing
    QUESTION,       // ?
    COLON,          // :
    NULL_COALESCE,  // ??

    // Arithmetic
    PLUS,           // +
    MINUS,          // -
    STAR,           // *
    SLASH,          // /
    PERCENT,        // %

    // String concatenation
    TILDE,          // ~

    // Filter pipe
    PIPE,           // |

    EOF
}
