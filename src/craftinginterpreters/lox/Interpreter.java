package craftinginterpreters.lox;

public class Interpreter implements Expr.Visitor<Object> {
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
