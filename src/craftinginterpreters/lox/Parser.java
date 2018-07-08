package craftinginterpreters.lox;

import java.util.List;

import static craftinginterpreters.lox.TokenType.*;

public class Parser {
    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens){
        this.tokens = tokens;
    }

    private Expr expression(){
        equality();
    }
}
