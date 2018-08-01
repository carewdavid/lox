package craftinginterpreters.lox;

import java.util.List;

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
    private Environment environment = new Environment();

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return environment.get(expr.name);
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
        }
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }

        return null;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        /* Short circuit evaluation of logical operators--like every other language out there. */
        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) {
                return left;
            }
        }else {
            if (!isTruthy(left)) {
                return left;
            }
        }

        return evaluate(expr.right);
    }

    private void executeBlock(List<Stmt> statements, Environment environment) {
        Environment prev = this.environment;
        try {
            this.environment = environment;

            for (Stmt stmt : statements) {
                execute(stmt);
            }
        } finally {
            this.environment = prev;
        }
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr);
        environment.assign(expr.name, value);
        return value;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        /* Lox variables default to null */
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name.lexeme, value);
        return null;
    }

    public void interpret(List<Stmt> statements){
        try {
            for (Stmt stmt : statements){
                execute(stmt);
            }
        } catch (RuntimeError err) {
            Lox.runtimeError(err);
        }
    }

    private void execute(Stmt statement) {
        statement.accept(this);
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    /* Convert a lox object to a string */
    private String stringify(Object obj){
        if (obj == null){
            return "nil";
        }

        if (obj instanceof Double) {
            String text = obj.toString();
            /* Strip off decimal point for whole numbers */
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return obj.toString();
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            /* Arithmetic */
            case MINUS:
                checkBinaryOperands(expr.operator, left, right);
                return (double)left - (double)right;
            case SLASH:
                checkBinaryOperands(expr.operator, left, right);
                return (double)left / (double)right;
            case STAR:
                checkBinaryOperands(expr.operator, left, right);
                return (double)left * (double)right;
            case PLUS:
                /* '+' is overloaded for addition and string concatenation. I'm going to break
                    with the book a little here and treat it as concatenation if _either_ of the
                    operands are strings instead of requiring them both to be.
                 */
                if (left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                }
                if (left instanceof String || right instanceof String) {
                    return left.toString() + right.toString();
                }
                throw new RuntimeError(expr.operator, "Operands must be numbers or strings.");
            case GREATER:
                return (double)left > (double)right;
            case GREATEREQ:
                return (double)left >= (double)right;
            case LESS:
                return (double)left > (double)right;
            case LESSEQ:
                return (double)left >= (double)right;
            case BANGEQ:
                return !isEqual(left, right);
            case EQEQ:
                return isEqual(left, right);
        }
        return null;
    }

    private void checkUnaryOperand(Token operator, Object operand){
        if (operand instanceof Double) {
            return;
        } else {
            throw new RuntimeError(operator, "Operand must be a number.");
        }
    }

    private void checkBinaryOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) {
            return;
        } else {
            throw new RuntimeError(operator, "Operands must be numbers.");
        }

    }

    private boolean isEqual(Object l, Object r){
        if (l == null && r == null){
            return true;
        }

        if (l == null){
            return false;
        }

        return l.equals(r);
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    private Object evaluate(Expr expr){
        return expr.accept(this);
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type){
            case MINUS:
                checkUnaryOperand(expr.operator, right);
                return -(double)right;
            case BANG:
                return isTruthy(right);
            default:
                return null;
        }

    }

    /* Nil and false are falsey, everything else is truthy */
    private boolean isTruthy(Object obj){
        if (obj == null) {
            return false;
        }
        if (obj instanceof Boolean){
            return (boolean)obj;
        }
        return true;
    }
}
