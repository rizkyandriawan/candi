package candi.compiler.lexer;

public enum TokenType {
    // Header directives
    PAGE,           // @page "/path"
    INJECT,         // @inject Type name
    INIT,           // @init
    ACTION,         // @action METHOD
    FRAGMENT_DEF,   // @fragment "name"
    LAYOUT,         // @layout "name"
    SLOT_FILL,      // @slot name

    // Directive arguments
    STRING_LITERAL, // "..."
    IDENTIFIER,     // variable/type names
    HTTP_METHOD,    // POST, PUT, DELETE, PATCH

    // Code blocks
    CODE_BLOCK,     // { ... } (brace-matched, opaque content)

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
    KEYWORD_FRAGMENT, // fragment (call)
    KEYWORD_COMPONENT, // component
    KEYWORD_SLOT,   // slot (render)

    // Expression tokens
    DOT,            // .
    NULL_SAFE_DOT,  // ?.
    LPAREN,         // (
    RPAREN,         // )
    COMMA,          // ,
    EQUALS_SIGN,    // = (for component param=val)
    EQ,             // ==
    NEQ,            // !=
    LT,             // <
    GT,             // >
    LTE,            // <=
    GTE,            // >=
    AND,            // &&
    OR,             // ||
    NOT,            // !
    NUMBER,         // numeric literal
    TRUE,           // true
    FALSE,          // false

    EOF
}
