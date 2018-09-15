package craftinginterpreters.tool;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class GenerateAst {
    public static void main(String[] args) throws IOException{
        if(args.length != 1){
            System.err.println("Usage: generate_ast <output directory>");
            System.exit(1);
        }
        String outputDir = args[0];
        defineAst(outputDir, "Expr", Arrays.asList(
                "This       : Token keyword",
                "Set        : Expr object, Token name, Expr value",
                "Get        : Expr object, Token name",
                "Call       : Expr callee, Token paren, List<Expr> arguments",
                "Binary     : Expr left, Token operator, Expr right",
                "Logical    : Expr left, Token operator, Expr right",
                "Grouping   : Expr expression",
                "Literal    : Object value",
                "Unary      : Token operator, Expr right",
                "Variable   : Token name",
                "Assign     : Token name, Expr value"
        ));

        defineAst(outputDir, "Stmt", Arrays.asList(
                "Class      : Token name, List<Stmt.Function> methods",
                "Return     : Token keyword, Expr value",
                "Function   : Token name, List<Token> parameters, List<Stmt> body",
                "While      : Expr condition, Stmt body",
                "If         : Expr condition, Stmt thenBranch, Stmt elseBranch",
                "Block      : List<Stmt> statements",
                "Expression : Expr expression",
                "Print      : Expr expression",
                "Var        : Token name, Expr initializer"
        ));
    }

    private static void defineAst(String outputDir, String baseName, List<String> types) throws IOException {
        String path = String.format("%s/%s.java", outputDir, baseName);
        System.out.println("Generating " + path + "...");
        /* Make sure there is a file to write to */
        File out = new File(path);
        out.createNewFile();

        PrintWriter writer = new PrintWriter(out, "UTF-8");
        writer.println("/* This file is automatically generated. Do not modify. */");
        writer.println("package craftinginterpreters.lox;");
        writer.println("");
        writer.println("import java.util.List;");
        writer.println("");
        writer.println("abstract class " + baseName + " {");

        defineVisitor(writer, baseName, types);
        for (String type : types){
            String className = type.split(":")[0].trim();
            String fields = type.split(":")[1].trim();
            defineType(writer, baseName, className, fields);
        }

        writer.println("");
        writer.println("    abstract <R> R accept(Visitor<R> visitor);");
        writer.println("}");
        writer.close();
    }

    private static void defineType(PrintWriter writer, String baseName, String className, String fieldList){
        writer.println("    static class " + className + " extends " + baseName + " {");

        /* Generate constructor */
        writer.println("        " + className + "(" + fieldList + ") {");
        String[] fields = fieldList.split(", ");
        for (String field: fields) {
            String name = field.split(" ")[1];
            writer.println("            this." + name + " = " + name + ";");
        }

        writer.println("        }");

        writer.println();
        writer.println("        <R> R accept(Visitor<R> visitor) {");
        writer.println("            return visitor.visit" + className + baseName +
        "(this);");
        writer.println("        }");

        writer.println();
        for (String field : fields){
            writer.println("        final " + field + ";");
        }
        writer.println("    }");
    }

    private static void defineVisitor(PrintWriter writer, String baseName, List<String> types){
        writer.println("    interface Visitor<R> {" );

        for (String type : types){
            String typeName = type.split(":")[0].trim();
            writer.println("        R visit" + typeName + baseName + "(" +
            typeName + " " + baseName.toLowerCase() + ");");

        }
        writer.println("    }");
    }
}
