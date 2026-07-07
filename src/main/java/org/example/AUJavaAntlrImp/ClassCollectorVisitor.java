package org.example.AUJavaAntlrImp;

import org.example.ClassInfo;
import org.example.ClassTable;
import org.example.ErrorHandler;
import org.example.AUJavaAntlr.AUJavaBaseVisitor;
import org.example.AUJavaAntlr.AUJavaParser;

/**
 * Pass 1: registers every class name (+ raw parent name text) into the ClassTable,
 * and locates the single valid "public static void main(String[] args)" entry point.
 */
public class ClassCollectorVisitor extends AUJavaBaseVisitor<Void> {

    private final ClassTable classTable;
    private final ErrorHandler errorHandler;

    private int validMainCount = 0;
    private String mainOwnerClassName;
    private AUJavaParser.BlockContext mainBodyCtx;

    // tracks the enclosing class name while walking members, so we can attribute a
    // discovered main() to the right owner class.
    private String currentClassName;

    public ClassCollectorVisitor(ClassTable classTable, ErrorHandler errorHandler) {
        this.classTable = classTable;
        this.errorHandler = errorHandler;
    }

    @Override
    public Void visitProgramDeclar(AUJavaParser.ProgramDeclarContext ctx) {
        visitChildren(ctx);

        if (validMainCount == 0) {
            errorHandler.error("no valid main method found — program needs exactly one method matching public static void main(String[] args)");
        } else if (validMainCount > 1) {
            errorHandler.error("multiple main methods found; exactly one is allowed");
        }

        return null;
    }

    @Override
    public Void visitClassDeclaration(AUJavaParser.ClassDeclarationContext ctx) {
        String name = ctx.name.getText();

        if (classTable.containsClass(name)) {
            errorHandler.error(ctx.name, "Class " + name + " is already defined");
        } else {
            ClassInfo classInfo = new ClassInfo(name);
            if (ctx.parent != null) {
                classInfo.setParentName(ctx.parent.getText());
            }
            classTable.putClass(classInfo);
        }

        String previousClassName = currentClassName;
        currentClassName = name;
        visitChildren(ctx);
        currentClassName = previousClassName;

        return null;
    }

    @Override
    public Void visitMainMethodDeclaration(AUJavaParser.MainMethodDeclarationContext ctx) {
        AUJavaParser.MainMethodContext mainCtx =
                (AUJavaParser.MainMethodContext) ctx.mainMethodDecl();

        boolean nameOk = mainCtx.name.getText().equals("main");
        boolean stringTypeOk = mainCtx.stringType.getText().equals("String");

        if (!nameOk || !stringTypeOk) {
            errorHandler.error(ctx, "expected 'public static void main(String[] args)'");
            return null;
        }

        validMainCount++;
        mainOwnerClassName = currentClassName;
        mainBodyCtx = mainCtx.block();

        return null;
    }

    public boolean hasErrors() {
        return errorHandler.hasErrors();
    }

    public String getMainOwnerClassName() {
        return mainOwnerClassName;
    }

    public AUJavaParser.BlockContext getMainBodyCtx() {
        return mainBodyCtx;
    }
}
