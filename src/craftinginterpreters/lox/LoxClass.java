package craftinginterpreters.lox;


import java.util.List;
import java.util.Map;

public class LoxClass implements LoxCallable{
    final String name;
    Map<String, LoxFunction> methods;


    public LoxClass(String name, Map<String, LoxFunction> methods) {
        this.name = name;
        this.methods = methods;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        LoxInstance instance = new LoxInstance(this);
        return instance;
    }

    public LoxFunction findMethod(LoxInstance instance, String name) {
        if (methods.containsKey(name)) {
            return  methods.get(name).bind(instance);
        }

        return null;
    }

    @Override
    public int arity() {
        return 0;
    }


}
