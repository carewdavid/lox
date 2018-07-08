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
}
