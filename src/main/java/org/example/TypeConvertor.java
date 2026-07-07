package org.example;

public class TypeConvertor {
    /**
     * Converts an AUJava type name to its C equivalent.
     * Primitives map directly; any other name is assumed to be a class name
     * and becomes a pointer to its struct.
     */
    public static String AUJavaType2cType(String AUJavaType) {
        if (AUJavaType == null) {
            return null;
        }
        switch (AUJavaType) {
            case "int":
                return "int";
            case "boolean":
                return "int";
            case "void":
                return "void";
            default:
                return "struct " + AUJavaType + "*";
        }
    }

    public static boolean isPrimitive(String AUJavaType) {
        return "int".equals(AUJavaType) || "boolean".equals(AUJavaType);
    }

    public static boolean isClassType(String AUJavaType) {
        return !"int".equals(AUJavaType) && !"boolean".equals(AUJavaType) && !"void".equals(AUJavaType);
    }
}
