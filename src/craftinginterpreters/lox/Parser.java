package craftinginterpreters.lox;

import java.util.List;

import static craftinginterpreters.lox.TokenType.*;

public class Parser {
    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens){
        this.tokens = tokens;
    }

    /* expression -> equality */
    private Expr expression(){
        return equality();
    }

    /* equality -> comparison ( ( "!=" | "==" ) comparison )* */
    private Expr equality(){
        Expr expr = comparison();

        while (match(BANGEQ, EQEQ)){
            Token op = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, op, right);
        }

        return expr;
    }

    /* comparison -> addition ( ( ">" | ">=" | "<" | "<=" ) addition )* */
    private Expr comparison(){
        Expr expr = addition();

        while (match(GREATER, GREATEREQ, LESS, LESSEQ)){
            Token op = previous();
            Expr right = addition();
            expr = new Expr.Binary(expr, op, right);
        }

        return expr;
    }

    /* addition -> multiplication ( ( "+" | "-" ) multiplication )* */
    private Expr addition(){
        Expr expr = multiplication();

        while (match(PLUS, MINUS)){
            Token op = previous();
            Expr right = multiplication();
            expr = new Expr.Binary(expr, op, right);
        }

        return expr;
    }


    /* Advance if next token is one of types */
    private boolean match(TokenType... types){
        for (TokenType type : types){
            if (check(type)){
                advance();
                return true;
            }
        }

        return false;
    }

    /* Check if the next token is type */
    private boolean check(TokenType type){
        if (isAtEnd()){
            return false;
        }

        return peek().type == type;
    }

    /* Consume the next token of input */
    private Token advance(){
        if (!isAtEnd()){
            current++;
        }

        return previous();
    }

    private boolean isAtEnd(){
        return peek().type == EOF;
    }

    /* Return the next token of input, but do not consume it */
    private Token peek(){
        return tokens.get(current);
    }

    /* Return the last token seen */
    private Token previous(){
        return tokens.get(current - 1);
    }
}
