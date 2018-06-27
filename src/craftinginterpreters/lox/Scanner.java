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

            default:
                Lox.error(line, "Unexpected character.");
                break;
        }
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
}
