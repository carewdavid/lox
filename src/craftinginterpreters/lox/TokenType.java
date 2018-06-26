package craftinginterpreters.lox;

public enum TokenType {
    /* Single character tokens */

    /* (, ), {, } */
    LPAREN, RPAREN, LBRACE, RBRACE,
    /* ,, ., -, +, ;, /, *, */
    COMMA, DOT, MINUS, PLUS, SEMICOLON, SLASH, STAR,

    /* 1-2 character tokens */

    /* !, !=, =, ==, >, >=, <, <= */
    BANG, BANGEQ, EQ, EQEQ, GREATER, GREATEREQ, LESS, LESSEQ,

    /* Literals */
    IDENTIFIER, STRING, NUMBER,

    /* Keywords */
    AND, CLASS, ELSE, FALSE, FUN, FOR, IF, NIL, OR, PRINT,
    RETURN, SUPER, THIS, TRUE, VAR, WHILE,
    EOF
}
