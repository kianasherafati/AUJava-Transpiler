package org.example;

import java.util.List;

public class MethodInfo {
    private final String name;
    private final String ownerClassName;
    private final String returnType;   // "int" | "boolean" | "void" | class name
    private final List<String> paramTypes;
    private final List<String> paramNames;
    private final boolean isStatic;
    private final Object bodyCtx; // the ANTLR method-body context, stored as Object to avoid a hard dep here

    public MethodInfo(String name, String ownerClassName, String returnType,
                       List<String> paramTypes, List<String> paramNames,
                       boolean isStatic, Object bodyCtx) {
        this.name = name;
        this.ownerClassName = ownerClassName;
        this.returnType = returnType;
        this.paramTypes = paramTypes;
        this.paramNames = paramNames;
        this.isStatic = isStatic;
        this.bodyCtx = bodyCtx;
    }

    public String getName() { return name; }
    public String getOwnerClassName() { return ownerClassName; }
    public String getReturnType() { return returnType; }
    public List<String> getParamTypes() { return paramTypes; }
    public List<String> getParamNames() { return paramNames; }
    public boolean isStatic() { return isStatic; }
    public Object getBodyCtx() { return bodyCtx; }

    public int arity() { return paramTypes.size(); }

    /** Mangled C function name: ClassName_methodName */
    public String getMangledName() {
        return ownerClassName + "_" + name;
    }

    /** True if `other` has the same name and identical param-type list (i.e. would collide/override). */
    public boolean hasSameSignature(MethodInfo other) {
        if (!this.name.equals(other.name)) return false;
        if (this.paramTypes.size() != other.paramTypes.size()) return false;
        for (int i = 0; i < this.paramTypes.size(); i++) {
            if (!this.paramTypes.get(i).equals(other.paramTypes.get(i))) return false;
        }
        return true;
    }
}
