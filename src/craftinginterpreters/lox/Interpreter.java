package craftinginterpreters.lox;

public class Interpreter implements Expr.Visitor<Object> {
    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            /* Arithmetic */
            case MINUS:
                return (double)left - (double)right;
            case SLASH:
                return (double)left / (double)right;
            case STAR:
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
        }
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
