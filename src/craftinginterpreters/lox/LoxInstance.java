package craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

public class LoxInstance {
    private LoxClass classs;
    private final Map<String, Object> fields = new HashMap<>();

    public LoxInstance(LoxClass classs) {
        this.classs = classs;
    }

    @Override
    public String toString() {
        return String.format("%s instance");
    }

    public Object get(Token name) {
        if (fields.containsKey(name.lexeme)) {
            return fields.get(name.lexeme);
        } else {
            throw new RuntimeError(name, String.format("Undefined property %s.", name.lexeme));
        }
    }

    public void set(Token name, Object value) {
        fields.put(name.lexeme, value);
    }

}
