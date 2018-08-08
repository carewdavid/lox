package craftinginterpreters.lox;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox {
    private static boolean hadError = false;
    private static boolean hadRuntimeError = false;
    private static final Interpreter interpreter = new Interpreter();

    public static void main(String[] args) throws IOException{
        if(args.length > 1) {
            System.out.println("Usage: jlox [script]");
        }else if (args.length == 1){
            runFile(args[0]);
        }else{
            runPrompt();
        }
    }

    /* Names of these next two methods should be self explanatory.
    runFile reads source code from file, runPrompt gives you a REPL */

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));
        if(hadError){
            System.exit(65);
        }
        if (hadRuntimeError) {
            System.exit(70);
        }
    }

    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        while(true){
            System.out.print("> ");
            run(reader.readLine());
            hadError = false;
        }

    }

    private static void run(String source){
        /* NB: This is a custom scanner, _not_ java.util's Scanner */
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        if (hadError){
            return;
        }

        Resolver resolver = new Resolver(interpreter);
        resolver.resolve(statements);
        if (hadError) {
            return;
        }

        interpreter.interpret(statements);
    }

    protected static void error(int line, String msg){
        report(line, "", msg);
    }

    protected static void error(Token tok, String message){
        if (tok.type == TokenType.EOF){
            report(tok.line, "at end", message);
        }else {
            report(tok.line, " at '" + tok.lexeme + "'", message);
        }
    }

    protected static void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() + "\n[line " + error.token.line +"]");
        hadRuntimeError = true;
    }

    private static void report(int line, String where, String message){
        System.err.printf("[line %s] Error %s: %s\n", line, where, message);
        hadError = true;
    }
}
