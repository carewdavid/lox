package craftinginterpreters.lox;

public class Interpreter implements Expr.Visitor<Object> {
    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        return null;
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return null;
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        return null;
    }
}
