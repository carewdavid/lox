package craftinginterpreters.lox;

import java.util.List;

public class LoxFunction implements LoxCallable{
    private final Stmt.Function declaration;

    LoxFunction(Stmt.Function declaration) {
        this.declaration = declaration;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Environment env = new Environment(interpreter.globals);
        for (int i = 0; i < declaration.parameters.size(); i++) {
            env.define(declaration.parameters.get(i).lexeme, arguments.get(i));
        }

        interpreter.executeBlock(declaration.body, env);
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
