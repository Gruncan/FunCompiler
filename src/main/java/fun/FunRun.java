package fun;

import ast.FunLexer;
import ast.FunParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.PrintStream;

/**
 * Driver for the Fun compiler and SVM interpreter.
 * Based on a previous version developed by
 * David Watt and Simon Gay (University of Glasgow).
 */
public class FunRun {

    private static final boolean tracing = true;

    private static final PrintStream out = System.out;

    public static void main(String[] args) {
        // Compile a Fun source program to SVM code,
        // then interpret it if it compiles successfully.
        // The source file name must be given as the
        // first program argument.
        try {
            if (args.length == 0) throw new FunException();
            SVM objProg = compile(args[0]);
            out.println("Interpretation ...");
            objProg.interpret(tracing);
        } catch (FunException x) {
            out.printf("Compilation failed %s\n", x.toString());
        } catch (Exception x) {
            x.printStackTrace(out);
        }
    }

    private static SVM compile(String filename) throws Exception {
        // Compile a Fun source program to SVM code.
        FunLexer lexer = new FunLexer(CharStreams.fromFileName(filename));
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        ParseTree tree = syntacticAnalyse(tokens);
        contextualAnalyse(tree, tokens);

        return codeGenerate(tree);
    }

    private static ParseTree syntacticAnalyse(CommonTokenStream tokens) throws Exception {
        // Perform syntactic analysis of a Fun source program.
        // Print any error messages.
        // Return a syntax tree representation of the Fun program.
        out.println();
        out.println("Syntactic analysis ...");

        FunParser parser = new FunParser(tokens);
        ParseTree tree = parser.program();

        int errors = parser.getNumberOfSyntaxErrors();
        out.println(errors + " syntactic errors");

        if (errors > 0) throw new FunException();

        return tree;
    }

    private static void contextualAnalyse(ParseTree tree, CommonTokenStream tokens) throws Exception {
        // Perform contextual analysis of a Fun program represented by a syntax tree.
        // Print any error messages.
        out.println("Contextual analysis ...");
        FunCheckerVisitor checker = new FunCheckerVisitor(tokens);
        checker.visit(tree);

        int errors = checker.getNumberOfContextualErrors();
        out.println(errors + " scope/type errors");
        out.println();
        if (errors > 0) throw new FunException();
    }

    private static SVM codeGenerate(ParseTree tree) {
        // Perform code generation of a Fun program,
        // represented by a syntax tree, emitting SVM code.
        // Also print the object code.
        out.println("Code generation ...");
        FunEncoderVisitor encoder = new FunEncoderVisitor();
        encoder.visit(tree);
        SVM objectProg = encoder.getSVM();

        out.println("Object code:");
        out.println(objectProg.showCode());

        return objectProg;
    }


}
