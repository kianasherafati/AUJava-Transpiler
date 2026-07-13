package org.example.AUJavaAntlrImp;

import org.antlr.v4.runtime.ParserRuleContext;
import org.example.AttributeHolder;
import org.example.ClassInfo;
import org.example.ClassTable;
import org.example.Environment;
import org.example.ErrorHandler;
import org.example.IntGenerator;
import org.example.MethodInfo;
import org.example.Symbol;
import org.example.TypeConvertor;
import org.example.AUJavaAntlr.AUJavaBaseVisitor;
import org.example.AUJavaAntlr.AUJavaParser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pass 3: semantic type-checking AND C code generation, done together via one tree
 * walk per method body. Bodies are pulled from the (fully populated) ClassTable, not
 * from a single parse-tree walk over the whole program.
 */
public class CodeGeneratorVisitor extends AUJavaBaseVisitor<AttributeHolder> {

    private final ClassTable classTable;
    private final ErrorHandler errorHandler;

    private final IntGenerator tempGen = new IntGenerator();
    private final IntGenerator labelGen = new IntGenerator();

    private ClassInfo currentClass;
    private MethodInfo currentMethod;
    private Environment currentEnvironment;

    // stack of [startLabel, endLabel] for the loop(s) we're currently nested inside.
    private final Deque<String[]> loopLabelStack = new ArrayDeque<>();

    public CodeGeneratorVisitor(ClassTable classTable, ErrorHandler errorHandler) {
        this.classTable = classTable;
        this.errorHandler = errorHandler;
    }

    public boolean hasErrors() {
        return errorHandler.hasErrors();
    }

    // ===================== Entry point =====================

    public String generateProgram(String mainOwnerClassName, AUJavaParser.BlockContext mainBody) {
        StringBuilder sb = new StringBuilder();

        sb.append("#include <stdio.h>\n#include <stdlib.h>\n\n");

        for (ClassInfo c : classTable.allClasses()) {
            sb.append("struct ").append(c.getName()).append(";\n");
        }
        sb.append("\n");

        for (ClassInfo c : structEmissionOrder()) {
            sb.append(generateStruct(c));
        }

        for (ClassInfo c : classTable.allClasses()) {
            for (Symbol staticField : c.getOwnStaticFields()) {
                sb.append(staticField.getcType()).append(" ")
                        .append(c.getName()).append("_").append(staticField.getName()).append(";\n");
            }
        }
        sb.append("\n");

        for (ClassInfo c : classTable.allClasses()) {
            for (MethodInfo m : c.getOwnMethods()) {
                sb.append(generateMethodBody(c, m, true));
            }
            for (MethodInfo m : c.getOwnStaticMethods()) {
                sb.append(generateMethodBody(c, m, false));
            }
        }

        for (ClassInfo c : classTable.allClasses()) {
            sb.append(generateConstructor(c));
        }

        tempGen.setCurrent(0);
        labelGen.setCurrent(0);
        currentClass = null;
        currentMethod = null;
        currentEnvironment = new Environment();
        loopLabelStack.clear();

        sb.append("int main() {\n");
        AttributeHolder mainResult = visitBlock(mainBody);
        sb.append(mainResult.getCode());
        sb.append("    return 0;\n}\n");

        return sb.toString();
    }

    // ===================== Struct / constructor / method-body emission =====================

    private List<ClassInfo> structEmissionOrder() {
        List<ClassInfo> all = new ArrayList<>();
        for (ClassInfo c : classTable.allClasses()) all.add(c);
        all.sort(java.util.Comparator.comparingInt(this::ancestorDepth));
        return all;
    }

    private int ancestorDepth(ClassInfo classInfo) {
        int depth = 0;
        for (ClassInfo c = classInfo.getParent(); c != null; c = c.getParent()) depth++;
        return depth;
    }

    private Map<String, MethodInfo> resolvableInstanceMethods(ClassInfo classInfo) {
        List<ClassInfo> chain = classInfo.selfAndAncestors();
        List<ClassInfo> rootFirst = new ArrayList<>(chain);
        java.util.Collections.reverse(rootFirst);
        Map<String, MethodInfo> resolved = new LinkedHashMap<>();
        for (ClassInfo c : rootFirst) {
            for (MethodInfo m : c.getOwnMethods()) {
                resolved.put(m.getName() + "/" + m.arity(), m);
            }
        }
        return resolved;
    }

    private String generateStruct(ClassInfo classInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("struct ").append(classInfo.getName()).append(" {\n");
        if (classInfo.getParent() != null) {
            sb.append("    struct ").append(classInfo.getParent().getName()).append(" super;\n");
        }
        for (Symbol field : classInfo.getOwnFields()) {
            sb.append("    ").append(field.getcType()).append(" ").append(field.getName()).append(";\n");
        }
        for (MethodInfo m : resolvableInstanceMethods(classInfo).values()) {
            sb.append("    ").append(TypeConvertor.AUJavaType2cType(m.getReturnType()))
                    .append(" (*function_").append(m.getName()).append(")(void*");
            for (String pt : m.getParamTypes()) {
                sb.append(", ").append(TypeConvertor.AUJavaType2cType(pt));
            }
            sb.append(");\n");
        }
        sb.append("};\n\n");
        return sb.toString();
    }

    private String generateMethodBody(ClassInfo classInfo, MethodInfo methodInfo, boolean isInstance) {
        tempGen.setCurrent(0);
        labelGen.setCurrent(0);
        loopLabelStack.clear();

        currentClass = classInfo;
        currentMethod = methodInfo;
        currentEnvironment = new Environment();

        StringBuilder sb = new StringBuilder();
        String retCType = TypeConvertor.AUJavaType2cType(methodInfo.getReturnType());

        sb.append(retCType).append(" ").append(methodInfo.getMangledName()).append("(");
        if (isInstance) {
            sb.append("void* caller");
            for (int i = 0; i < methodInfo.arity(); i++) {
                sb.append(", ").append(TypeConvertor.AUJavaType2cType(methodInfo.getParamTypes().get(i)))
                        .append(" ").append(methodInfo.getParamNames().get(i));
            }
            sb.append(") {\n");
            sb.append("    struct ").append(classInfo.getName()).append("* self = (struct ")
                    .append(classInfo.getName()).append("*)caller;\n");
        } else {
            for (int i = 0; i < methodInfo.arity(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(TypeConvertor.AUJavaType2cType(methodInfo.getParamTypes().get(i)))
                        .append(" ").append(methodInfo.getParamNames().get(i));
            }
            sb.append(") {\n");
        }

        for (int i = 0; i < methodInfo.arity(); i++) {
            String paramType = methodInfo.getParamTypes().get(i);
            currentEnvironment.putSymbol(new Symbol(methodInfo.getParamNames().get(i), Symbol.KIND_PARAM,
                    paramType, TypeConvertor.AUJavaType2cType(paramType)));
        }

        AUJavaParser.BlockContext body = (AUJavaParser.BlockContext) methodInfo.getBodyCtx();
        AttributeHolder bodyResult = visitBlock(body);
        sb.append(bodyResult.getCode());
        sb.append("}\n\n");

        currentClass = null;
        currentMethod = null;
        currentEnvironment = null;

        return sb.toString();
    }

    private String generateConstructor(ClassInfo classInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("struct ").append(classInfo.getName()).append("* new_").append(classInfo.getName()).append("() {\n");
        sb.append("    struct ").append(classInfo.getName()).append("* instance = malloc(sizeof(struct ")
                .append(classInfo.getName()).append("));\n");

        Map<String, MethodInfo> resolved = resolvableInstanceMethods(classInfo);
        for (MethodInfo m : resolved.values()) {
            sb.append("    instance->function_").append(m.getName()).append(" = ")
                    .append(m.getMangledName()).append(";\n");
        }

        StringBuilder superPrefix = new StringBuilder();
        for (ClassInfo ancestor = classInfo.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            superPrefix.append("super.");
            Map<String, MethodInfo> ancestorSlots = resolvableInstanceMethods(ancestor);
            for (String key : ancestorSlots.keySet()) {
                MethodInfo mostDerived = resolved.get(key);
                sb.append("    instance->").append(superPrefix).append("function_")
                        .append(mostDerived.getName()).append(" = ").append(mostDerived.getMangledName()).append(";\n");
            }
        }

        sb.append("    return instance;\n}\n\n");
        return sb.toString();
    }

    // ===================== Shared helpers =====================

    private String newTemp() {
        return "_t_" + tempGen.generate();
    }

    private String newLabel(String prefix) {
        return "_L_" + prefix + "_" + labelGen.generate();
    }

    private String castedAddress(String declaredType, AttributeHolder rhs) {
        if (TypeConvertor.isClassType(declaredType) && !declaredType.equals(rhs.getAUJavaType())
                && rhs.getAddress() != null && !rhs.getAddress().isEmpty()) {
            return "(" + TypeConvertor.AUJavaType2cType(declaredType) + ")" + rhs.getAddress();
        }
        return rhs.getAddress();
    }

    private boolean assignable(String declaredType, String actualType) {
        if (declaredType == null || actualType == null) return false;
        if (declaredType.equals(actualType)) return true;
        if (TypeConvertor.isPrimitive(declaredType) || TypeConvertor.isPrimitive(actualType)) return false;
        if ("void".equals(declaredType) || "void".equals(actualType)) return false;
        ClassInfo declaredClass = classTable.getClass(declaredType);
        ClassInfo actualClass = classTable.getClass(actualType);
        if (declaredClass == null || actualClass == null) return false;
        return actualClass.isSubclassOf(declaredClass);
    }

    private boolean equalityCompatible(String t1, String t2) {
        if (t1.equals(t2)) return true;
        if (TypeConvertor.isPrimitive(t1) || TypeConvertor.isPrimitive(t2)) return false;
        if ("void".equals(t1) || "void".equals(t2)) return false;
        ClassInfo c1 = classTable.getClass(t1);
        ClassInfo c2 = classTable.getClass(t2);
        if (c1 == null || c2 == null) return false;
        return c1.isSubclassOf(c2) || c2.isSubclassOf(c1);
    }

    private ClassInfo findFieldOwner(ClassInfo fromClass, String fieldName) {
        for (ClassInfo c = fromClass; c != null; c = c.getParent()) {
            if (c.declaresField(fieldName)) return c;
        }
        return null;
    }

    private String fieldAccessChain(ClassInfo fromClass, String fieldName) {
        StringBuilder sb = new StringBuilder();
        for (ClassInfo c = fromClass; c != null; c = c.getParent()) {
            if (c.declaresField(fieldName)) {
                return sb.toString();
            }
            sb.append("super.");
        }
        return sb.toString();
    }

    private AttributeHolder fieldAccessAttr(ClassInfo declClassSearchFrom, String fieldName, String receiverAddress,
                                            ParserRuleContext errCtx, boolean requireInstanceContext) {
        AttributeHolder result = new AttributeHolder();
        Symbol field = declClassSearchFrom.resolveField(fieldName);
        if (field == null) {
            errorHandler.error(errCtx, "field '" + fieldName + "' is not defined in class " + declClassSearchFrom.getName());
            result.setAUJavaType("int");
            result.setcType("int");
            result.setAddress("0");
            return result;
        }
        if (requireInstanceContext && !field.isStatic() && currentMethod != null && currentMethod.isStatic()) {
            errorHandler.error(errCtx, "cannot access instance field '" + fieldName + "' from a static context");
        }
        if (field.isStatic()) {
            ClassInfo owner = findFieldOwner(declClassSearchFrom, fieldName);
            result.setAddress(owner.getName() + "_" + fieldName);
        } else {
            String chain = fieldAccessChain(declClassSearchFrom, fieldName);
            result.setAddress(receiverAddress + "->" + chain + fieldName);
        }
        result.setAUJavaType(field.getAUJavaType());
        result.setcType(field.getcType());
        return result;
    }

    private String AUJavaTypeFromTypeCtx(AUJavaParser.TypeContext ctx) {
        if (ctx instanceof AUJavaParser.IntTypeContext) return "int";
        if (ctx instanceof AUJavaParser.BooleanTypeContext) return "boolean";
        if (ctx instanceof AUJavaParser.VoidTypeContext) return "void";
        if (ctx instanceof AUJavaParser.ClassTypeContext) {
            return ((AUJavaParser.ClassTypeContext) ctx).ID().getText();
        }
        return "int";
    }

    // ===================== Statements =====================

    @Override
    public AttributeHolder visitBlock(AUJavaParser.BlockContext ctx) {
        Environment previous = currentEnvironment;
        currentEnvironment = new Environment(previous);
        AttributeHolder result = new AttributeHolder();
        result.appendToCurrentCode("{\n");
        for (AUJavaParser.StatementContext stmt : ctx.statement()) {
            AttributeHolder stmtResult = visit(stmt);
            result.appendToCurrentCode(stmtResult.getCode());
        }
        result.appendToCurrentCode("}\n");
        currentEnvironment = previous;
        return result;
    }

    @Override
    public AttributeHolder visitBlockStatement(AUJavaParser.BlockStatementContext ctx) {
        return visit(ctx.block());
    }

    @Override
    public AttributeHolder visitLocalVarDeclStatement(AUJavaParser.LocalVarDeclStatementContext ctx) {
        return visit(ctx.localVarDecl());
    }

    @Override
    public AttributeHolder visitLocalVariableDeclaration(AUJavaParser.LocalVariableDeclarationContext ctx) {
        AttributeHolder result = new AttributeHolder();
        String name = ctx.ID().getText();
        String AUJavaType = AUJavaTypeFromTypeCtx(ctx.type());
        String cType = TypeConvertor.AUJavaType2cType(AUJavaType);

        if (currentEnvironment.containsSymbolWithName(name)) {
            errorHandler.error(ctx, "variable '" + name + "' is already declared in this scope");
        }

        result.appendToCurrentCode(cType + " " + name + ";\n");

        if (ctx.expr() != null) {
            AttributeHolder rhs = visit(ctx.expr());
            result.appendToCurrentCode(rhs.getCode());
            if (!assignable(AUJavaType, rhs.getAUJavaType())) {
                errorHandler.error(ctx, "cannot assign value of type '" + rhs.getAUJavaType()
                        + "' to variable '" + name + "' of type '" + AUJavaType + "'");
            }
            result.appendToCurrentCode(name + " = " + castedAddress(AUJavaType, rhs) + ";\n");
        }

        currentEnvironment.putSymbol(new Symbol(name, Symbol.KIND_VARIABLE, AUJavaType, cType));
        return result;
    }

    @Override
    public AttributeHolder visitAssignmentStatement(AUJavaParser.AssignmentStatementContext ctx) {
        return visit(ctx.assign());
    }

    @Override
    public AttributeHolder visitAssignment(AUJavaParser.AssignmentContext ctx) {
        AttributeHolder targetInfo = resolveLValue(ctx.target);
        AttributeHolder rhs = visit(ctx.expr());

        AttributeHolder result = new AttributeHolder();
        result.appendToCurrentCode(targetInfo.getCode());
        result.appendToCurrentCode(rhs.getCode());

        if (!assignable(targetInfo.getAUJavaType(), rhs.getAUJavaType())) {
            errorHandler.error(ctx, "cannot assign value of type '" + rhs.getAUJavaType()
                    + "' to target of type '" + targetInfo.getAUJavaType() + "'");
        }

        result.appendToCurrentCode(targetInfo.getAddress() + " = " + castedAddress(targetInfo.getAUJavaType(), rhs) + ";\n");
        result.setAddress(targetInfo.getAddress());
        result.setAUJavaType(targetInfo.getAUJavaType());
        result.setcType(targetInfo.getcType());
        return result;
    }

    private static final String CLASS_REF_PREFIX = "$classref$";

    private boolean isClassRef(String AUJavaType) {
        return AUJavaType != null && AUJavaType.startsWith(CLASS_REF_PREFIX);
    }

    private String classRefName(String AUJavaType) {
        return AUJavaType.substring(CLASS_REF_PREFIX.length());
    }

    private AttributeHolder resolveLValue(AUJavaParser.LvalueContext ctx) {
        if (ctx instanceof AUJavaParser.FieldLValueContext) {
            AUJavaParser.FieldLValueContext fctx = (AUJavaParser.FieldLValueContext) ctx;
            AttributeHolder receiver = visit(fctx.receiver);
            String fieldName = fctx.field.getText();
            AttributeHolder result;
            if (isClassRef(receiver.getAUJavaType())) {
                ClassInfo classInfo = classTable.getClass(classRefName(receiver.getAUJavaType()));
                result = fieldAccessAttr(classInfo, fieldName, "", ctx, false);
            } else if (receiver.getAUJavaType() == null || TypeConvertor.isPrimitive(receiver.getAUJavaType())
                    || "void".equals(receiver.getAUJavaType())) {
                errorHandler.error(ctx, "cannot access field '" + fieldName + "' on a non-object type");
                result = new AttributeHolder();
                result.setAUJavaType("int");
                result.setcType("int");
                result.setAddress("0");
            } else {
                ClassInfo receiverClass = classTable.getClass(receiver.getAUJavaType());
                if (receiverClass == null) {
                    errorHandler.error(ctx, "unknown class '" + receiver.getAUJavaType() + "'");
                    result = new AttributeHolder();
                    result.setAUJavaType("int");
                    result.setcType("int");
                    result.setAddress("0");
                } else {
                    result = fieldAccessAttr(receiverClass, fieldName, receiver.getAddress(), ctx, false);
                }
            }
            result.setCode(receiver.getCode() + result.getCode());
            return result;
        } else if (ctx instanceof AUJavaParser.ThisFieldLValueContext) {
            AUJavaParser.ThisFieldLValueContext tctx = (AUJavaParser.ThisFieldLValueContext) ctx;
            String fieldName = tctx.ID().getText();
            if (currentMethod != null && currentMethod.isStatic()) {
                errorHandler.error(ctx, "'this' cannot be used in a static context");
            }
            if (currentClass == null) {
                errorHandler.error(ctx, "'this' cannot be used outside of an instance method");
                AttributeHolder dummy = new AttributeHolder();
                dummy.setAUJavaType("int");
                dummy.setcType("int");
                dummy.setAddress("0");
                return dummy;
            }
            return fieldAccessAttr(currentClass, fieldName, "self", ctx, true);
        } else {
            AUJavaParser.IdLValueContext ictx = (AUJavaParser.IdLValueContext) ctx;
            String name = ictx.ID().getText();
            Symbol local = currentEnvironment.getSymbol(name);
            if (local != null) {
                AttributeHolder result = new AttributeHolder();
                result.setAddress(name);
                result.setAUJavaType(local.getAUJavaType());
                result.setcType(local.getcType());
                return result;
            }
            if (currentClass != null) {
                Symbol field = currentClass.resolveField(name);
                if (field != null) {
                    return fieldAccessAttr(currentClass, name, "self", ctx, true);
                }
            }
            errorHandler.error(ctx, "variable " + name + " is not defined in this scope");
            AttributeHolder dummy = new AttributeHolder();
            dummy.setAUJavaType("int");
            dummy.setcType("int");
            dummy.setAddress("0");
            return dummy;
        }
    }

    @Override
    public AttributeHolder visitExpressionStatement(AUJavaParser.ExpressionStatementContext ctx) {
        AttributeHolder e = visit(ctx.expr());
        AttributeHolder result = new AttributeHolder();
        result.appendToCurrentCode(e.getCode());
        return result;
    }

    @Override
    public AttributeHolder visitIfStatement(AUJavaParser.IfStatementContext ctx) {
        AttributeHolder cond = visit(ctx.expr());
        AttributeHolder result = new AttributeHolder();
        result.appendToCurrentCode(cond.getCode());

        if (!"boolean".equals(cond.getAUJavaType())) {
            errorHandler.error(ctx, "if condition must be of type boolean");
        }

        String elseLabel = newLabel("if_else");
        String endLabel = newLabel("if_end");

        result.appendToCurrentCode("if (!(" + cond.getAddress() + ")) goto " + elseLabel + ";\n");
        AttributeHolder thenResult = visit(ctx.thenStat);
        result.appendToCurrentCode(thenResult.getCode());
        result.appendToCurrentCode("goto " + endLabel + ";\n");
        result.appendToCurrentCode(elseLabel + ":;\n");
        if (ctx.elseStat != null) {
            AttributeHolder elseResult = visit(ctx.elseStat);
            result.appendToCurrentCode(elseResult.getCode());
        }
        result.appendToCurrentCode(endLabel + ":;\n");
        return result;
    }

    @Override
    public AttributeHolder visitWhileStatement(AUJavaParser.WhileStatementContext ctx) {
        String startLabel = newLabel("loop_start");
        String endLabel = newLabel("loop_end");

        AttributeHolder result = new AttributeHolder();
        result.appendToCurrentCode(startLabel + ":;\n");

        AttributeHolder cond = visit(ctx.expr());
        if (!"boolean".equals(cond.getAUJavaType())) {
            errorHandler.error(ctx, "while condition must be of type boolean");
        }
        result.appendToCurrentCode(cond.getCode());
        result.appendToCurrentCode("if (!(" + cond.getAddress() + ")) goto " + endLabel + ";\n");

        loopLabelStack.push(new String[]{startLabel, endLabel});
        AttributeHolder body = visit(ctx.statement());
        loopLabelStack.pop();

        result.appendToCurrentCode(body.getCode());
        result.appendToCurrentCode("goto " + startLabel + ";\n");
        result.appendToCurrentCode(endLabel + ":;\n");
        return result;
    }

    @Override
    public AttributeHolder visitBreakStatement(AUJavaParser.BreakStatementContext ctx) {
        AttributeHolder result = new AttributeHolder();
        if (loopLabelStack.isEmpty()) {
            errorHandler.error(ctx, "'break' outside of a loop");
            return result;
        }
        result.appendToCurrentCode("goto " + loopLabelStack.peek()[1] + ";\n");
        return result;
    }

    @Override
    public AttributeHolder visitContinueStatement(AUJavaParser.ContinueStatementContext ctx) {
        AttributeHolder result = new AttributeHolder();
        if (loopLabelStack.isEmpty()) {
            errorHandler.error(ctx, "'continue' outside of a loop");
            return result;
        }
        result.appendToCurrentCode("goto " + loopLabelStack.peek()[0] + ";\n");
        return result;
    }

    @Override
    public AttributeHolder visitReturnStatement(AUJavaParser.ReturnStatementContext ctx) {
        AttributeHolder result = new AttributeHolder();
        String expectedType = currentMethod != null ? currentMethod.getReturnType() : "void";

        if (ctx.expr() != null) {
            if ("void".equals(expectedType)) {
                errorHandler.error(ctx, "void method cannot return a value");
            }
            AttributeHolder val = visit(ctx.expr());
            result.appendToCurrentCode(val.getCode());
            if (!"void".equals(expectedType) && !assignable(expectedType, val.getAUJavaType())) {
                errorHandler.error(ctx, "cannot return value of type '" + val.getAUJavaType()
                        + "' from method with return type '" + expectedType + "'");
            }
            result.appendToCurrentCode("return " + castedAddress(expectedType, val) + ";\n");
        } else {
            if (!"void".equals(expectedType)) {
                errorHandler.error(ctx, "missing return value for method with return type '" + expectedType + "'");
            }
            result.appendToCurrentCode("return;\n");
        }
        return result;
    }

    @Override
    public AttributeHolder visitPrintStatement(AUJavaParser.PrintStatementContext ctx) {
        AttributeHolder val = visit(ctx.expr());
        AttributeHolder result = new AttributeHolder();
        result.appendToCurrentCode(val.getCode());
        if (!"int".equals(val.getAUJavaType())) {
            errorHandler.error(ctx, "System.out.println argument must be of type int");
        }
        result.appendToCurrentCode("printf(\"%d\\n\", " + val.getAddress() + ");\n");
        return result;
    }

    // ===================== Expressions =====================

    @Override
    public AttributeHolder visitNotExpression(AUJavaParser.NotExpressionContext ctx) {
        AttributeHolder operand = visit(ctx.expr());
        AttributeHolder result = new AttributeHolder();
        result.appendToCurrentCode(operand.getCode());
        if (!"boolean".equals(operand.getAUJavaType())) {
            errorHandler.error(ctx, "operator '!' requires a boolean operand");
        }
        String temp = newTemp();
        result.appendToCurrentCode("int " + temp + " = !(" + operand.getAddress() + ");\n");
        result.setAddress(temp);
        result.setAUJavaType("boolean");
        result.setcType("int");
        return result;
    }

    @Override
    public AttributeHolder visitMulDivModExpression(AUJavaParser.MulDivModExpressionContext ctx) {
        AttributeHolder left = visit(ctx.leftSide);
        AttributeHolder right = visit(ctx.rightSide);
        AttributeHolder result = new AttributeHolder();
        result.appendToCurrentCode(left.getCode());
        result.appendToCurrentCode(right.getCode());
        if (!"int".equals(left.getAUJavaType()) || !"int".equals(right.getAUJavaType())) {
            errorHandler.error(ctx, "operator '" + ctx.op.getText() + "' requires int operands");
        }
        String temp = newTemp();
        result.appendToCurrentCode("int " + temp + " = " + left.getAddress() + " " + ctx.op.getText() + " " + right.getAddress() + ";\n");
        result.setAddress(temp);
        result.setAUJavaType("int");
        result.setcType("int");
        return result;
    }

    @Override
    public AttributeHolder visitAddSubExpression(AUJavaParser.AddSubExpressionContext ctx) {
        AttributeHolder left = visit(ctx.leftSide);
        AttributeHolder right = visit(ctx.rightSide);
        AttributeHolder result = new AttributeHolder();
        result.appendToCurrentCode(left.getCode());
        result.appendToCurrentCode(right.getCode());
        if (!"int".equals(left.getAUJavaType()) || !"int".equals(right.getAUJavaType())) {
            errorHandler.error(ctx, "operator '" + ctx.op.getText() + "' requires int operands");
        }
        String temp = newTemp();
        result.appendToCurrentCode("int " + temp + " = " + left.getAddress() + " " + ctx.op.getText() + " " + right.getAddress() + ";\n");
        result.setAddress(temp);
        result.setAUJavaType("int");
        result.setcType("int");
        return result;
    }

    @Override
    public AttributeHolder visitRelationalExpression(AUJavaParser.RelationalExpressionContext ctx) {
        AttributeHolder left = visit(ctx.leftSide);
        AttributeHolder right = visit(ctx.rightSide);
        AttributeHolder result = new AttributeHolder();
        result.appendToCurrentCode(left.getCode());
        result.appendToCurrentCode(right.getCode());
        if (!"int".equals(left.getAUJavaType()) || !"int".equals(right.getAUJavaType())) {
            errorHandler.error(ctx, "operator '" + ctx.op.getText() + "' requires int operands");
        }
        String temp = newTemp();
        result.appendToCurrentCode("int " + temp + " = " + left.getAddress() + " " + ctx.op.getText() + " " + right.getAddress() + ";\n");
        result.setAddress(temp);
        result.setAUJavaType("boolean");
        result.setcType("int");
        return result;
    }

    @Override
    public AttributeHolder visitEqualityExpression(AUJavaParser.EqualityExpressionContext ctx) {
        AttributeHolder left = visit(ctx.leftSide);
        AttributeHolder right = visit(ctx.rightSide);
        AttributeHolder result = new AttributeHolder();
        result.appendToCurrentCode(left.getCode());
        result.appendToCurrentCode(right.getCode());
        if (!equalityCompatible(left.getAUJavaType(), right.getAUJavaType())) {
            errorHandler.error(ctx, "operator '" + ctx.op.getText() + "' requires operands of compatible types");
        }
        String temp = newTemp();
        result.appendToCurrentCode("int " + temp + " = (" + left.getAddress() + " " + ctx.op.getText() + " " + right.getAddress() + ");\n");
        result.setAddress(temp);
        result.setAUJavaType("boolean");
        result.setcType("int");
        return result;
    }

    @Override
    public AttributeHolder visitAndExpression(AUJavaParser.AndExpressionContext ctx) {
        AttributeHolder left = visit(ctx.leftSide);
        AttributeHolder right = visit(ctx.rightSide);
        AttributeHolder result = new AttributeHolder();
        result.appendToCurrentCode(left.getCode());
        result.appendToCurrentCode(right.getCode());
        if (!"boolean".equals(left.getAUJavaType()) || !"boolean".equals(right.getAUJavaType())) {
            errorHandler.error(ctx, "operator '&&' requires boolean operands");
        }
        String temp = newTemp();
        result.appendToCurrentCode("int " + temp + " = (" + left.getAddress() + " && " + right.getAddress() + ");\n");
        result.setAddress(temp);
        result.setAUJavaType("boolean");
        result.setcType("int");
        return result;
    }

    @Override
    public AttributeHolder visitOrExpression(AUJavaParser.OrExpressionContext ctx) {
        AttributeHolder left = visit(ctx.leftSide);
        AttributeHolder right = visit(ctx.rightSide);
        AttributeHolder result = new AttributeHolder();
        result.appendToCurrentCode(left.getCode());
        result.appendToCurrentCode(right.getCode());
        if (!"boolean".equals(left.getAUJavaType()) || !"boolean".equals(right.getAUJavaType())) {
            errorHandler.error(ctx, "operator '||' requires boolean operands");
        }
        String temp = newTemp();
        result.appendToCurrentCode("int " + temp + " = (" + left.getAddress() + " || " + right.getAddress() + ");\n");
        result.setAddress(temp);
        result.setAUJavaType("boolean");
        result.setcType("int");
        return result;
    }

    @Override
    public AttributeHolder visitNewObjectExpression(AUJavaParser.NewObjectExpressionContext ctx) {
        String className = ctx.ID().getText();
        AttributeHolder result = new AttributeHolder();
        if (!classTable.containsClass(className)) {
            errorHandler.error(ctx, "class '" + className + "' is not defined");
            result.setAUJavaType("int");
            result.setcType("int");
            result.setAddress("0");
            return result;
        }
        String temp = newTemp();
        result.appendToCurrentCode("struct " + className + "* " + temp + " = new_" + className + "();\n");
        result.setAddress(temp);
        result.setAUJavaType(className);
        result.setcType("struct " + className + "*");
        return result;
    }

    @Override
    public AttributeHolder visitThisExpression(AUJavaParser.ThisExpressionContext ctx) {
        AttributeHolder result = new AttributeHolder();
        if (currentMethod != null && currentMethod.isStatic()) {
            errorHandler.error(ctx, "'this' cannot be used in a static context");
        }
        if (currentClass == null) {
            errorHandler.error(ctx, "'this' cannot be used outside of an instance method");
            result.setAUJavaType("int");
            result.setcType("int");
            result.setAddress("0");
            return result;
        }
        result.setAddress("self");
        result.setAUJavaType(currentClass.getName());
        result.setcType("struct " + currentClass.getName() + "*");
        return result;
    }

    @Override
    public AttributeHolder visitFieldAccessExpression(AUJavaParser.FieldAccessExpressionContext ctx) {
        AttributeHolder receiver = visit(ctx.receiver);
        String fieldName = ctx.field.getText();
        AttributeHolder result = new AttributeHolder();
        result.appendToCurrentCode(receiver.getCode());

        boolean staticAccess = isClassRef(receiver.getAUJavaType());
        ClassInfo receiverClass;
        String receiverAddress;
        if (staticAccess) {
            receiverClass = classTable.getClass(classRefName(receiver.getAUJavaType()));
            receiverAddress = "";
        } else if (receiver.getAUJavaType() == null || TypeConvertor.isPrimitive(receiver.getAUJavaType())
                || "void".equals(receiver.getAUJavaType())) {
            errorHandler.error(ctx, "cannot access field '" + fieldName + "' on a non-object type");
            result.setAUJavaType("int");
            result.setcType("int");
            result.setAddress("0");
            return result;
        } else {
            receiverClass = classTable.getClass(receiver.getAUJavaType());
            receiverAddress = receiver.getAddress();
            if (receiverClass == null) {
                errorHandler.error(ctx, "unknown class '" + receiver.getAUJavaType() + "'");
                result.setAUJavaType("int");
                result.setcType("int");
                result.setAddress("0");
                return result;
            }
        }

        AttributeHolder fieldAttr = fieldAccessAttr(receiverClass, fieldName, receiverAddress, ctx, false);
        result.appendToCurrentCode(fieldAttr.getCode());
        result.setAddress(fieldAttr.getAddress());
        result.setAUJavaType(fieldAttr.getAUJavaType());
        result.setcType(fieldAttr.getcType());
        return result;
    }

    @Override
    public AttributeHolder visitMethodCallExpression(AUJavaParser.MethodCallExpressionContext ctx) {
        AttributeHolder receiver = visit(ctx.receiver);
        String methodName = ctx.method.getText();
        AttributeHolder result = new AttributeHolder();
        result.appendToCurrentCode(receiver.getCode());

        boolean staticAccess = isClassRef(receiver.getAUJavaType());
        ClassInfo receiverClass;
        if (staticAccess) {
            receiverClass = classTable.getClass(classRefName(receiver.getAUJavaType()));
        } else if (receiver.getAUJavaType() == null || TypeConvertor.isPrimitive(receiver.getAUJavaType())
                || "void".equals(receiver.getAUJavaType())) {
            errorHandler.error(ctx, "cannot call method '" + methodName + "' on a non-object type");
            result.setAUJavaType("int");
            result.setcType("int");
            result.setAddress("0");
            return result;
        } else {
            receiverClass = classTable.getClass(receiver.getAUJavaType());
            if (receiverClass == null) {
                errorHandler.error(ctx, "unknown class '" + receiver.getAUJavaType() + "'");
                result.setAUJavaType("int");
                result.setcType("int");
                result.setAddress("0");
                return result;
            }
        }

        List<AUJavaParser.ExprContext> argExprs = ((AUJavaParser.ArgumentsContext) ctx.argList()).expr();
        MethodInfo method = staticAccess
                ? receiverClass.resolveStaticMethod(methodName, argExprs.size())
                : receiverClass.resolveMethod(methodName, argExprs.size());
        if (method == null) {
            errorHandler.error(ctx, "method '" + methodName + "' with " + argExprs.size()
                    + " argument(s) not found on class " + receiverClass.getName());
            result.setAUJavaType("int");
            result.setcType("int");
            result.setAddress("0");
            return result;
        }

        List<String> argAddrs = new ArrayList<>();
        for (int i = 0; i < argExprs.size(); i++) {
            AttributeHolder argVal = visit(argExprs.get(i));
            result.appendToCurrentCode(argVal.getCode());
            String expectedType = method.getParamTypes().get(i);
            if (!assignable(expectedType, argVal.getAUJavaType())) {
                errorHandler.error(ctx, "argument " + (i + 1) + " of call to '" + methodName
                        + "' expects type '" + expectedType + "' but got '" + argVal.getAUJavaType() + "'");
            }
            argAddrs.add(castedAddress(expectedType, argVal));
        }

        String callExpr;
        if (staticAccess) {
            callExpr = method.getMangledName() + "(" + String.join(", ", argAddrs) + ")";
        } else {
            callExpr = "((struct " + receiverClass.getName() + "*)" + receiver.getAddress() + ")->function_"
                    + methodName + "(" + receiver.getAddress() + (argAddrs.isEmpty() ? "" : ", " + String.join(", ", argAddrs)) + ")";
        }

        if ("void".equals(method.getReturnType())) {
            result.appendToCurrentCode(callExpr + ";\n");
            result.setAddress("");
            result.setAUJavaType("void");
            result.setcType("void");
        } else {
            String retCType = TypeConvertor.AUJavaType2cType(method.getReturnType());
            String temp = newTemp();
            result.appendToCurrentCode(retCType + " " + temp + " = " + callExpr + ";\n");
            result.setAddress(temp);
            result.setAUJavaType(method.getReturnType());
            result.setcType(retCType);
        }
        return result;
    }

    @Override
    public AttributeHolder visitUnqualifiedCallExpression(AUJavaParser.UnqualifiedCallExpressionContext ctx) {
        String methodName = ctx.ID().getText();
        List<AUJavaParser.ExprContext> argExprs = ((AUJavaParser.ArgumentsContext) ctx.argList()).expr();
        AttributeHolder result = new AttributeHolder();

        MethodInfo instanceMethod = null;
        MethodInfo staticMethod = null;
        if (currentClass != null) {
            if (currentMethod == null || !currentMethod.isStatic()) {
                instanceMethod = currentClass.resolveMethod(methodName, argExprs.size());
            }
            if (instanceMethod == null) {
                staticMethod = currentClass.resolveStaticMethod(methodName, argExprs.size());
            }
        }

        if (instanceMethod == null && staticMethod == null) {
            errorHandler.error(ctx, "method '" + methodName + "' with " + argExprs.size() + " argument(s) not found");
            result.setAUJavaType("int");
            result.setcType("int");
            result.setAddress("0");
            return result;
        }

        MethodInfo method = instanceMethod != null ? instanceMethod : staticMethod;

        List<String> argAddrs = new ArrayList<>();
        for (int i = 0; i < argExprs.size(); i++) {
            AttributeHolder argVal = visit(argExprs.get(i));
            result.appendToCurrentCode(argVal.getCode());
            String expectedType = method.getParamTypes().get(i);
            if (!assignable(expectedType, argVal.getAUJavaType())) {
                errorHandler.error(ctx, "argument " + (i + 1) + " of call to '" + methodName
                        + "' expects type '" + expectedType + "' but got '" + argVal.getAUJavaType() + "'");
            }
            argAddrs.add(castedAddress(expectedType, argVal));
        }

        String callExpr;
        if (instanceMethod != null) {
            callExpr = "self->function_" + methodName + "(self" + (argAddrs.isEmpty() ? "" : ", " + String.join(", ", argAddrs)) + ")";
        } else {
            callExpr = method.getMangledName() + "(" + String.join(", ", argAddrs) + ")";
        }

        if ("void".equals(method.getReturnType())) {
            result.appendToCurrentCode(callExpr + ";\n");
            result.setAddress("");
            result.setAUJavaType("void");
            result.setcType("void");
        } else {
            String retCType = TypeConvertor.AUJavaType2cType(method.getReturnType());
            String temp = newTemp();
            result.appendToCurrentCode(retCType + " " + temp + " = " + callExpr + ";\n");
            result.setAddress(temp);
            result.setAUJavaType(method.getReturnType());
            result.setcType(retCType);
        }
        return result;
    }

    @Override
    public AttributeHolder visitAtomExpression(AUJavaParser.AtomExpressionContext ctx) {
        return visit(ctx.atom());
    }

    @Override
    public AttributeHolder visitIntegerAtom(AUJavaParser.IntegerAtomContext ctx) {
        AttributeHolder result = new AttributeHolder();
        result.setAddress(ctx.INTEGER().getText());
        result.setAUJavaType("int");
        result.setcType("int");
        return result;
    }

    @Override
    public AttributeHolder visitTrueAtom(AUJavaParser.TrueAtomContext ctx) {
        AttributeHolder result = new AttributeHolder();
        result.setAddress("1");
        result.setAUJavaType("boolean");
        result.setcType("int");
        return result;
    }

    @Override
    public AttributeHolder visitFalseAtom(AUJavaParser.FalseAtomContext ctx) {
        AttributeHolder result = new AttributeHolder();
        result.setAddress("0");
        result.setAUJavaType("boolean");
        result.setcType("int");
        return result;
    }

    @Override
    public AttributeHolder visitIDAtom(AUJavaParser.IDAtomContext ctx) {
        String name = ctx.ID().getText();
        Symbol local = currentEnvironment.getSymbol(name);
        if (local != null) {
            AttributeHolder result = new AttributeHolder();
            result.setAddress(name);
            result.setAUJavaType(local.getAUJavaType());
            result.setcType(local.getcType());
            return result;
        }
        if (currentClass != null) {
            Symbol field = currentClass.resolveField(name);
            if (field != null) {
                return fieldAccessAttr(currentClass, name, "self", ctx, true);
            }
        }
        if (classTable.containsClass(name)) {
            AttributeHolder result = new AttributeHolder();
            result.setAUJavaType(CLASS_REF_PREFIX + name);
            result.setcType("");
            result.setAddress("");
            return result;
        }
        errorHandler.error(ctx, "variable " + name + " is not defined in this scope");
        AttributeHolder result = new AttributeHolder();
        result.setAUJavaType("int");
        result.setcType("int");
        result.setAddress("0");
        return result;
    }

    @Override
    public AttributeHolder visitParentAtom(AUJavaParser.ParentAtomContext ctx) {
        return visit(ctx.expr());
    }
}