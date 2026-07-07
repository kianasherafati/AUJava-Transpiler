package org.example.AUJavaAntlrImp;

import org.example.ClassInfo;
import org.example.ClassTable;
import org.example.ErrorHandler;
import org.example.MethodInfo;
import org.example.Symbol;
import org.example.TypeConvertor;
import org.example.AUJavaAntlr.AUJavaBaseVisitor;
import org.example.AUJavaAntlr.AUJavaParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pass 2: resolves inheritance (parent pointers + cycle detection), then walks each
 * class's members to populate its fields and method signatures.
 *
 * Must run after ClassCollectorVisitor (Pass 1), which has already registered every
 * class name and raw parent-name text.
 */
public class MemberCollectorVisitor extends AUJavaBaseVisitor<Void> {

    private final ClassTable classTable;
    private final ErrorHandler errorHandler;

    public MemberCollectorVisitor(ClassTable classTable, ErrorHandler errorHandler) {
        this.classTable = classTable;
        this.errorHandler = errorHandler;
    }

    /** Call once, before visiting the parse tree, to resolve inheritance. */
    public void resolveInheritance() {
        // Step 1: resolve parent pointers from parent-name text.
        for (ClassInfo classInfo : classTable.allClasses()) {
            String parentName = classInfo.getParentName();
            if (parentName == null) {
                continue;
            }
            ClassInfo parent = classTable.getClass(parentName);
            if (parent == null) {
                errorHandler.error("class " + classInfo.getName() + " extends undefined class " + parentName);
            } else {
                classInfo.setParent(parent);
            }
        }

        // Step 2: detect cyclic inheritance.
        int classCount = 0;
        for (ClassInfo ignored : classTable.allClasses()) {
            classCount++;
        }
        int maxSteps = classCount + 1;

        for (ClassInfo classInfo : classTable.allClasses()) {
            Set<String> seen = new HashSet<>();
            ClassInfo current = classInfo;
            int steps = 0;
            while (current != null && steps <= maxSteps) {
                if (seen.contains(current.getName())) {
                    errorHandler.error("cyclic inheritance detected involving class " + classInfo.getName());
                    break;
                }
                seen.add(current.getName());
                current = current.getParent();
                steps++;
            }
        }
    }

    /** Call once, after visiting the whole parse tree, to enforce the static-override rule. */
    public void checkStaticOverrides() {
        for (ClassInfo classInfo : classTable.allClasses()) {
            for (MethodInfo staticMethod : classInfo.getOwnStaticMethods()) {
                ClassInfo ancestor = classInfo.getParent();
                while (ancestor != null) {
                    if (ancestor.declaresMethod(staticMethod.getName(), staticMethod.arity())) {
                        // declaresMethod checks both static/instance maps; only care about static clash here.
                        for (MethodInfo ancestorStatic : ancestor.getOwnStaticMethods()) {
                            if (ancestorStatic.getName().equals(staticMethod.getName())
                                    && ancestorStatic.arity() == staticMethod.arity()) {
                                errorHandler.error("static method " + staticMethod.getName()
                                        + " cannot be overridden in subclass " + classInfo.getName());
                            }
                        }
                    }
                    ancestor = ancestor.getParent();
                }
            }
        }
    }

    @Override
    public Void visitClassDeclaration(AUJavaParser.ClassDeclarationContext ctx) {
        String className = ctx.name.getText();
        ClassInfo classInfo = classTable.getClass(className);
        if (classInfo == null) {
            // Should not happen if Pass 1 ran correctly, but guard anyway.
            return null;
        }

        for (AUJavaParser.MemberContext memberCtx : ctx.member()) {
            if (memberCtx instanceof AUJavaParser.FieldMemberContext) {
                handleField(classInfo, (AUJavaParser.FieldMemberContext) memberCtx);
            } else if (memberCtx instanceof AUJavaParser.MethodMemberContext) {
                handleMethod(classInfo, className, (AUJavaParser.MethodMemberContext) memberCtx);
            }
        }

        return null;
    }

    private void handleField(ClassInfo classInfo, AUJavaParser.FieldMemberContext memberCtx) {
        AUJavaParser.FieldDeclarationContext fieldCtx =
                (AUJavaParser.FieldDeclarationContext) memberCtx.fieldDecl();

        boolean isStatic = fieldCtx.STATIC() != null;
        String AUJavaType = resolveTypeText(fieldCtx.type());
        String idText = fieldCtx.ID().getText();

        if (classInfo.declaresField(idText)) {
            errorHandler.error(fieldCtx, "field " + idText + " is already defined in class " + classInfo.getName());
            return;
        }

        Symbol field = new Symbol(idText, Symbol.KIND_FIELD, AUJavaType,
                TypeConvertor.AUJavaType2cType(AUJavaType), isStatic);
        classInfo.addField(field);
    }

    private void handleMethod(ClassInfo classInfo, String ownerClassName, AUJavaParser.MethodMemberContext memberCtx) {
        AUJavaParser.MethodDeclContext methodDeclCtx = memberCtx.methodDecl();

        if (methodDeclCtx instanceof AUJavaParser.MainMethodDeclarationContext) {
            // The unique program entry point is already tracked by ClassCollectorVisitor
            // (Pass 1); registering it again here as a regular method isn't needed since
            // Pass 3 retrieves the main body via ClassCollectorVisitor's getters directly.
            return;
        }

        if (!(methodDeclCtx instanceof AUJavaParser.RegularMethodDeclarationContext)) {
            return;
        }

        AUJavaParser.RegularMethodDeclarationContext regularCtx =
                (AUJavaParser.RegularMethodDeclarationContext) methodDeclCtx;

        boolean isStatic = regularCtx.STATIC() != null;
        String returnType = resolveTypeText(regularCtx.type());
        String methodName = regularCtx.ID().getText();

        List<String> paramTypes = new ArrayList<>();
        List<String> paramNames = new ArrayList<>();

        AUJavaParser.ParamListContext paramListCtx = regularCtx.paramList();
        if (paramListCtx instanceof AUJavaParser.ParametersContext) {
            AUJavaParser.ParametersContext parametersCtx = (AUJavaParser.ParametersContext) paramListCtx;
            for (AUJavaParser.ParamContext paramCtx : parametersCtx.param()) {
                AUJavaParser.ParamDeclarationContext paramDeclCtx = (AUJavaParser.ParamDeclarationContext) paramCtx;
                paramTypes.add(resolveTypeText(paramDeclCtx.type()));
                paramNames.add(paramDeclCtx.ID().getText());
            }
        }

        if (classInfo.declaresMethod(methodName, paramTypes.size())) {
            errorHandler.error(regularCtx, "method " + methodName + " is already defined with "
                    + paramTypes.size() + " parameters");
            return;
        }

        MethodInfo methodInfo = new MethodInfo(methodName, ownerClassName, returnType,
                paramTypes, paramNames, isStatic, regularCtx.block());
        classInfo.addMethod(methodInfo);
    }

    private String resolveTypeText(AUJavaParser.TypeContext typeCtx) {
        if (typeCtx instanceof AUJavaParser.IntTypeContext) {
            return "int";
        } else if (typeCtx instanceof AUJavaParser.BooleanTypeContext) {
            return "boolean";
        } else if (typeCtx instanceof AUJavaParser.VoidTypeContext) {
            return "void";
        } else if (typeCtx instanceof AUJavaParser.ClassTypeContext) {
            return ((AUJavaParser.ClassTypeContext) typeCtx).ID().getText();
        }
        return typeCtx.getText();
    }
}
