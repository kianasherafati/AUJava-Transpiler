package org.example;

import java.util.Objects;

public class Symbol {
    public static final String KIND_VARIABLE = "variable";
    public static final String KIND_FIELD = "field";
    public static final String KIND_PARAM = "param";

    private String name;
    private String kind;
    private String AUJavaType;
    private String cType;
    private boolean isStatic;

    public Symbol(String name, String kind, String AUJavaType, String cType) {
        this(name, kind, AUJavaType, cType, false);
    }

    public Symbol(String name, String kind, String AUJavaType, String cType, boolean isStatic) {
        this.name = name;
        this.kind = kind;
        this.AUJavaType = AUJavaType;
        this.cType = cType;
        this.isStatic = isStatic;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Symbol symbol = (Symbol) o;
        return Objects.equals(name, symbol.name) && Objects.equals(AUJavaType, symbol.AUJavaType);
    }

    public String getName() {
        return name;
    }

    public String getcType() {
        return cType;
    }

    public String getAUJavaType() {
        return AUJavaType;
    }

    public String getKind() {
        return kind;
    }

    public boolean isStatic() {
        return isStatic;
    }

    /** True if AUJavaType refers to a user-defined class rather than a primitive/void. */
    public boolean isObjectType() {
        return !AUJavaType.equals("int") && !AUJavaType.equals("boolean") && !AUJavaType.equals("void");
    }
}
