package craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static craftinginterpreters.lox.TokenType.*;

public class Scanner {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    private int start = 0;
    private int current = 0;
    private int line = 1;

    public Scanner(String source) {
        this.source = source;
    }

    protected List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }
        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    private void scanToken() {
        char c = advance();

        switch (c) {
            case '(':
                addToken(LPAREN);
                break;
            case ')':
                addToken(RPAREN);
                break;
            case '{':
                addToken(LBRACE);
                break;
            case '}':
                addToken(RBRACE);
                break;
            case ',':
                addToken(COMMA);
                break;
            case '.':
                addToken(DOT);
                break;
            case '-':
                addToken(MINUS);
                break;
            case '+':
                addToken(PLUS);
                break;
            case '*':
                addToken(STAR);
                break;
            case ';':
                addToken(SEMICOLON);
                break;
            case '!':
                addToken(match('=') ? BANGEQ : BANG);
                break;
            case '=':
                addToken(match('=') ? EQEQ : EQ);
                break;
            case '>':
                addToken(match('=') ? GREATEREQ : GREATER);
                break;
            case '<':
                addToken(match('=') ? LESSEQ : LESS);
                break;
            case '/':
                if(match('/')){
                    /* Eat comments */
                    while(peek() != '\n' && !isAtEnd()) advance();
                }else{
                    addToken(SLASH);
                }
                break;
            /* Handle whitespace and newlines */
            case ' ':
            case '\r':
            case '\t':
                break;
            case '\n':
                line++;
                break;

            case '"':
                string();
                break;
            default:
                if(isDigit(c)){
                    number();
                }else {
                    Lox.error(line, "Unexpected character.");
                }
                break;
        }
    }

    private void string(){
        while(peek() != '"' && !isAtEnd()){
            /* Increment line position in multiline strings */
            if (peek() == '\n') {
                line++;
            }
            advance();

            if (isAtEnd()) {
               Lox.error(line, "Unterminated string.");
               return;
            }

            /* Consume closing '"' */
            advance();

            String val = source.substring(start+1, current-1);
            addToken(STRING, val);

        }
    }

    private void number(){
        while (isDigit(peek())){
            advance();
        }

        if(peek() == '.' && isDigit(peekNext())){
            advance();
        }

        while (isDigit(peek())) {
            advance();
        }

        addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
    }

    private char advance() {
        current++;
        return source.charAt(current - 1);
    }

    private boolean match(char expected) {
        if (isAtEnd()) {
            return false;
        }

        if (source.charAt(current) != expected) {
            return false;
        }

        current++;
        return true;
    }

    private char peek(){
        if(isAtEnd()){
            return '\0';

        }else{
            return source.charAt(current);
        }
    }

    /* Turns put we need 2 characters of lookahead :( */
    private char peekNext(){
        if (current + 1 >= source.length()){
            return '\0';
        }
        return source.charAt(current+1);
    }

    private void addToken(TokenType type) {
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    /* Check if c is an ASCII digit - no unicode here */
    private boolean isDigit(char c){
        return c >= '0' && c <= '9';
    }
}
