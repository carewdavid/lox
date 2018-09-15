package craftinginterpreters.lox;

import java.util.List;

public class LoxFunction implements LoxCallable{
    private final Environment closure;
    private final Stmt.Function declaration;
    private final boolean isInitializer;

    LoxFunction(Stmt.Function declaration, Environment closure, boolean isInitializer) {
        this.declaration = declaration;
        this.closure = closure;
        this.isInitializer = isInitializer;
    }

    LoxFunction bind(LoxInstance instance) {
        Environment environment = new Environment(closure);
        environment.define("this", instance);
        return new LoxFunction(declaration, environment, false);
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

            if (isInitializer) {
                return closure.getAt(0, "this");
            }

            return ret.value;
        }

        /*
         An object's constructor returns that object - calling the constructor after the object has been created
         yields the same instance of the object
         */
        if (isInitializer) {
            return closure.getAt(0, "this");
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
