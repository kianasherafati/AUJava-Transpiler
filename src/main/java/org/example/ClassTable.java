package org.example;

import java.util.LinkedHashMap;
import java.util.Map;

public class ClassTable {
    private final Map<String, ClassInfo> classes = new LinkedHashMap<>();

    public boolean containsClass(String name) {
        return classes.containsKey(name);
    }

    public ClassInfo getClass(String name) {
        return classes.get(name);
    }

    public void putClass(ClassInfo classInfo) {
        classes.put(classInfo.getName(), classInfo);
    }

    public Iterable<ClassInfo> allClasses() {
        return classes.values();
    }
}
