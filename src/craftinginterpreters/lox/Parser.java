package craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static craftinginterpreters.lox.TokenType.*;

public class Parser {

    private static class ParseError extends RuntimeException{}

    private final List<Token> tokens;
    private int current = 0;

    private final int MAX_PARAMETERS = 8;

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
    declaration -> funDecl
    declaration -> classDecl
     */

    private Stmt declaration() {
        try {
            if (match(VAR)) {
                return varDeclaration();
            }

            if (match(FUN)) {
                return funDeclaration("function");
            }

            if (match(CLASS)) {
                return classDeclaration();
            }

            return statement();

        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    /* classDecl -> "class" IDENTIFIER ( "<" IDENTIFIER )? "{" function* "}" */
    private Stmt classDeclaration() {
        Token name = consume(IDENTIFIER, "Expect class name.");

        Expr.Variable superclass = null;
        if (match(LESS)) {
            consume(IDENTIFIER, "Expect name of superclass.");
            superclass = new Expr.Variable(previous());
        }
        consume(LBRACE, "Expect '{' before class body.");
        List<Stmt.Function> methods = new ArrayList<>();
        while (!check(RBRACE) && !isAtEnd()) {
            methods.add(funDeclaration("method"));
        }

        consume(RBRACE, "Expect '}' after class body.");

        return new Stmt.Class(name, superclass, methods);
    }

    /*
    funDecl -> "fun" function

    function -> IDENTIFIER "(" parameters? ")" block

    parameters -> IDENTIFIER ( "," IDENTIFIER )*

     */
    private Stmt.Function funDeclaration(String kind){
        Token name = consume(IDENTIFIER, String.format("Expect %s name.", kind));
        consume(LPAREN, String.format("Expect '(' after %s name.", kind));
        List<Token> parameters = new ArrayList<>();

        /*Are there any parameters declared? */
        if (!check(RPAREN)) {
            do {
                if (parameters.size() >= MAX_PARAMETERS) {
                    error(peek(), String.format("Cannot have more than %d parameters.", MAX_PARAMETERS));
                }
                parameters.add(consume(IDENTIFIER, "Expect parameter name."));
            }while (match(COMMA));
        }
        consume(RPAREN, "Expect ')' after function parameters.");

        consume(LBRACE, String.format("Expect '{' before %s body.", kind));
        List<Stmt> body = block();
        return new Stmt.Function(name, parameters, body);
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
    statement -> whileStmt
    statement -> forStmt
    statement -> returnStmt
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

        if (match(WHILE)) {
            return whileStatement();
        }

        if (match(FOR)) {
            return forStatement();
        }

        if (match(RETURN)) {
            return returnStatement();
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

    /* whileStmt -> "while" "(" expression ")" statement */
    private Stmt whileStatement() {
        consume(LPAREN, "Expect '(' after while.");
        Expr condition = expression();
        consume(RPAREN, "Expect ')' after loop condition.");
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    /*
    forStmt -> "for" "(" ( varDecl | exprStmt | ";" ) expression? ";" expression? ")" statement
    */
    private Stmt forStatement() {
        consume(LPAREN, "Expect '(' after for.");
        Stmt initializer = null;

        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");

        Expr update = null;
        if (!check(RPAREN)){
            update = expression();
        }
        consume(RPAREN, "Expect ')' after for clauses.");

        /* Desugar for loops into while loops */
        Stmt body = statement();
        if (update != null) {
            body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(update)));
        }
        if (condition == null) {
            condition = new Expr.Literal(true);
        }
        body = new Stmt.While(condition, body);

        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
    }

    /* returnStmt -> "return" expression? ";" */
    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;

        if (!check(SEMICOLON)) {
            value = expression();
        }

        consume(SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    /* expression -> assignment */
    private Expr expression(){
        return assignment();
    }

    /*
    assignment -> ( call "." )? IDENTIFIER "=" assignment
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
            } else if (expr instanceof Expr.Get) {
                Expr.Get get = (Expr.Get)expr;
                return new Expr.Set(get.object, get.name, value);
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
       unary -> call
     */
    private Expr unary(){
       if (match(BANG, MINUS)){
           Token op = previous();
           return new Expr.Unary(op, unary());
       }else {
           return call();
       }

    }

    /* call -> primary ( "(" arguments? ")" | "." IDENTIFIER )* */
    private Expr call() {
        Expr expr = primary();
        while (true) {
            if (match(LPAREN)) {
                expr = finishCall(expr);
            } else if (match(DOT)) {
                Token name = consume(IDENTIFIER, "Expect property name after '.'.");
                expr = new Expr.Get(expr, name);
            } else {
                break;
            }
        }
        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> args = new ArrayList<>();
        if (!check(RPAREN)) {
            do {
                if (args.size() >= MAX_PARAMETERS) {
                    error(peek(), String.format("Functions cannot have more than %d arguments.", MAX_PARAMETERS));
                }
                args.add(expression());
            } while (match(COMMA));
        }
        /* Grab the closing paren to use its location in error messages */
        Token paren = consume(RPAREN, "Expect ')' after function arguments.");

        return new Expr.Call(callee, paren, args);
    }
    /* primary -> NUMBER | STRING | IDENTIFIER | "false" | "true" | "nil" | "this" | "super" | "(" expression ")" */
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

        if (match(THIS)) {
            return new Expr.This(previous());
        }

        if (match(SUPER)) {
            Token keyword = previous();
            consume(DOT, "Expect '.' after 'super'.");
            Token method = consume(IDENTIFIER, "Expect superclass method name.");
            return new Expr.Super(keyword, method);
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
