package org.example;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.tree.ParseTree;
import org.example.AUJavaAntlr.AUJavaLexer;
import org.example.AUJavaAntlr.AUJavaParser;
import org.example.AUJavaAntlrImp.ClassCollectorVisitor;
import org.example.AUJavaAntlrImp.CodeGeneratorVisitor;
import org.example.AUJavaAntlrImp.MemberCollectorVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class App
{
    private static String readFileContent(String programFileName){
        String fileContent = "";
        try{
            Path path = Paths.get(programFileName);
            fileContent = Files.readString(path);
        } catch(IOException e){
            e.printStackTrace();
            System.exit(-1);
        }
        return fileContent;
    }

    private static void writeStringToFile(String string, String fileAddress){
        try{
            Files.write(Paths.get(fileAddress), string.getBytes());
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }
    public static void main( String[] args )
    {
        if(args.length == 0){
            System.err.println("Provide a program file address");
            System.exit(-1);
        }

        String programFileName = args[0];
        String programFileContent = readFileContent(programFileName);

        ErrorHandler errorHandler = new ErrorHandler();

        ANTLRInputStream input = new ANTLRInputStream(programFileContent);
        AUJavaLexer lexer = new AUJavaLexer(input);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        AUJavaParser parser = new AUJavaParser(tokenStream);

        BaseErrorListener syntaxErrorListener = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                                    int charPositionInLine, String msg, RecognitionException e) {
                errorHandler.error("Error at line " + line + ":" + charPositionInLine + " - " + msg);
            }
        };
        lexer.removeErrorListeners();
        lexer.addErrorListener(syntaxErrorListener);
        parser.removeErrorListeners();
        parser.addErrorListener(syntaxErrorListener);

        ParseTree parseTree = parser.program();
        if (errorHandler.hasErrors()) {
            System.exit(-1);
        }

        ClassTable classTable = new ClassTable();

        // Pass 1: register every class name + parent-name text, locate the unique main().
        ClassCollectorVisitor classCollector = new ClassCollectorVisitor(classTable, errorHandler);
        classCollector.visit(parseTree);
        if (classCollector.hasErrors()) {
            System.exit(-1);
        }

        // Pass 2: resolve inheritance (+ cycle detection), collect fields/method signatures,
        // then enforce the static-override rule once every class's members are known.
        MemberCollectorVisitor memberCollector = new MemberCollectorVisitor(classTable, errorHandler);
        memberCollector.resolveInheritance();
        memberCollector.visit(parseTree);
        memberCollector.checkStaticOverrides();
        if (errorHandler.hasErrors()) {
            System.exit(-1);
        }

        // Pass 3: semantic-check + generate C code for every method body and for main().
        CodeGeneratorVisitor codeGenerator = new CodeGeneratorVisitor(classTable, errorHandler);
        String outputCode = codeGenerator.generateProgram(
                classCollector.getMainOwnerClassName(),
                classCollector.getMainBodyCtx()
        );
        if (codeGenerator.hasErrors()) {
            System.exit(-1);
        }

        String outputFileName = "result.c";
        if(args.length >= 2){
            outputFileName = args[1];
        }

        writeStringToFile(outputCode, outputFileName);
    }
}