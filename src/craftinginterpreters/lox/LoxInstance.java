package craftinginterpreters.lox;

public class LoxInstance {
    private LoxClass classs;

    public LoxInstance(LoxClass classs) {
        this.classs = classs;
    }

    @Override
    public String toString() {
        return String.format("%s instance");
    }
}
