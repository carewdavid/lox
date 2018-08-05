package craftinginterpreters.lox;

import java.util.List;

public class LoxFunction implements LoxCallable{
    private final Environment closure;
    private final Stmt.Function declaration;

    LoxFunction(Stmt.Function declaration, Environment closure) {
        this.declaration = declaration;
        this.closure = closure;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment env = new Environment(closure);
        for (int i = 0; i < declaration.parameters.size(); i++) {
            env.define(declaration.parameters.get(i).lexeme, arguments.get(i));
        }

        try {
            interpreter.executeBlock(declaration.body, env);
        } catch (Return ret) {
            return ret.value;
        }
        return null;
    }

    @Override
    public int arity() {
        return declaration.parameters.size();
    }

    @Override
    public String toString() {
        return String.format("<fn %d>", declaration.name.lexeme);
    }
}
