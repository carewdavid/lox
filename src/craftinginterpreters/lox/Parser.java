package craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

import static craftinginterpreters.lox.TokenType.*;

public class Parser {

    private static class ParseError extends RuntimeException{}

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens){
        this.tokens = tokens;
    }

    /* program -> declaration* EOF */
    protected List<Stmt> parse(){
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    /*
    declaration -> varDecl
    declaration -> statement
     */

    private Stmt declaration() {
        try {
            if (match(VAR)) {
                return varDeclaration();
            } else {
                return statement();
            }
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    /* varDecl -> "var" IDENTIFIER ( "=" expression )? ";" */
    private Stmt varDeclaration(){
        Token name = consume(IDENTIFIER, "Expect variable name");

        Expr initializer = null;
        if (match(EQ)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' at end of declaration.");
        return new Stmt.Var(name, initializer);
    }

    /*
    statement -> exprStmt
    statement -> printStmt
    statement -> ifStmt
    statement -> block
     */
    private Stmt statement() {
        if (match(PRINT)) {
            return printStatement();
        }
        if (match(LBRACE)) {
            return new Stmt.Block(block());
        }

        if (match(IF)) {
            return ifStatement();
        }
        return expressionStatement();

    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' at end of statement");
        return new Stmt.Print(value);
    }

    private Stmt expressionStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' at end of statement");
        return new Stmt.Expression(value);
    }

    /* ifStmt -> "if" "(" expression ")" statement ( "else" statement )? */
    private Stmt ifStatement() {
        consume(LPAREN, "Expect '(' after 'if'.");

        Expr condition = expression();
        consume(RPAREN, "Expect ')' after condition.");
        Stmt then = statement();
        Stmt elsee = null;
        if (match(ELSE)){
            elsee = statement();
        }

         return new Stmt.If(condition, then, elsee);
    }

    /* block -> "{" declaration* "}" */
    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RBRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RBRACE, "Expect '}' at end of block.");
        return statements;
    }

    /* expression -> assignment */
    private Expr expression(){
        return assignment();
    }

    /*
    assignment -> identifier "=" assignment
    assignment -> logic_or
     */
    private Expr assignment(){

        Expr expr = or();
        if (match(EQ)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof  Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid lvalue in assignment.");
        }
        return expr;
    }

    /* logic_or -> logic_and ( "or" logic_and )* */
    private Expr or() {
        Expr expr = and();
        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    /* logic_and -> equality ( "and" equality )* */
    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
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

    /* multiplication -> unary ( ( "*" | "/" ) unary)* */
    private Expr multiplication(){
        Expr expr = unary();

        while (match(STAR, SLASH)){
            Token op = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, op, right);
        }

        return expr;
    }

    /*
       unary -> ( "!" | "-" ) unary
       unary -> primary
     */
    private Expr unary(){
       if (match(BANG, MINUS)){
           Token op = previous();
           return new Expr.Unary(op, unary());
       }else {
           return primary();
       }

    }
    /* primary -> NUMBER | STRING | IDENTIFIER | "false" | "true" | "nil" | "(" expression ")" */
    private Expr primary(){
        if (match(FALSE)){
            return new Expr.Literal(false);
        }

        if (match(TRUE)){
            return new Expr.Literal(true);
        }

        if (match(NIL)){
            return new Expr.Literal(null);
        }

        if (match(NUMBER, STRING)){
            return new Expr.Literal(previous().literal);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LPAREN)){
            Expr expr = expression();
            consume(RPAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }
        throw error(peek(), "Expect expression");
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

    private Token consume(TokenType type, String message){
        if (check(type)){
            return advance();
        }
        throw error(peek(), message);
    }

    private ParseError error(Token tok, String message){
        Lox.error(tok, message);
        return new ParseError();
    }

    private void synchronize(){
        advance();

        while (!isAtEnd()){
            if (previous().type == SEMICOLON){
                return;
            }

            switch (peek().type) {
                case FOR:
                case FUN:
                case VAR:
                case IF:
                case CLASS:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }
            advance();
        }
    }
}
