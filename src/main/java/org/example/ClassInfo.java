package org.example;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClassInfo {
    private final String name;
    private String parentName;       // null if no explicit extends
    private ClassInfo parent;        // resolved in Pass 2, after all classes collected

    // ordered so struct field order / constructor emission order is deterministic
    private final Map<String, Symbol> fields = new LinkedHashMap<>();
    private final Map<String, Symbol> staticFields = new LinkedHashMap<>();

    // keyed by "name/arity" to allow overload-free but arity-distinguishable lookups;
    // duplicate-signature detection is done explicitly when adding.
    private final Map<String, MethodInfo> methods = new LinkedHashMap<>();
    private final Map<String, MethodInfo> staticMethods = new LinkedHashMap<>();

    public ClassInfo(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName; }

    public ClassInfo getParent() { return parent; }
    public void setParent(ClassInfo parent) { this.parent = parent; }

    public void addField(Symbol field) {
        if (field.isStatic()) {
            staticFields.put(field.getName(), field);
        } else {
            fields.put(field.getName(), field);
        }
    }

    public boolean declaresField(String name) {
        return fields.containsKey(name) || staticFields.containsKey(name);
    }

    /** Own fields only, in declaration order. */
    public List<Symbol> getOwnFields() { return new ArrayList<>(fields.values()); }
    public List<Symbol> getOwnStaticFields() { return new ArrayList<>(staticFields.values()); }

    /** Walks the parent chain (self first) to resolve a field by name. */
    public Symbol resolveField(String name) {
        for (ClassInfo c = this; c != null; c = c.parent) {
            if (c.fields.containsKey(name)) return c.fields.get(name);
            if (c.staticFields.containsKey(name)) return c.staticFields.get(name);
        }
        return null;
    }

    public void addMethod(MethodInfo method) {
        if (method.isStatic()) {
            staticMethods.put(method.getName() + "/" + method.arity(), method);
        } else {
            methods.put(method.getName() + "/" + method.arity(), method);
        }
    }

    public boolean declaresMethod(String name, int arity) {
        return methods.containsKey(name + "/" + arity) || staticMethods.containsKey(name + "/" + arity);
    }

    public List<MethodInfo> getOwnMethods() { return new ArrayList<>(methods.values()); }
    public List<MethodInfo> getOwnStaticMethods() { return new ArrayList<>(staticMethods.values()); }

    /** Walks the parent chain (self first) to resolve an instance method by name+arity. */
    public MethodInfo resolveMethod(String name, int arity) {
        for (ClassInfo c = this; c != null; c = c.parent) {
            MethodInfo m = c.methods.get(name + "/" + arity);
            if (m != null) return m;
        }
        return null;
    }

    /** Walks the parent chain (self first) to resolve a static method by name+arity. */
    public MethodInfo resolveStaticMethod(String name, int arity) {
        for (ClassInfo c = this; c != null; c = c.parent) {
            MethodInfo m = c.staticMethods.get(name + "/" + arity);
            if (m != null) return m;
        }
        return null;
    }

    /** True if `possibleAncestor` is this class or one of its ancestors. */
    public boolean isSubclassOf(ClassInfo possibleAncestor) {
        for (ClassInfo c = this; c != null; c = c.parent) {
            if (c == possibleAncestor) return true;
        }
        return false;
    }

    /** Full ancestor chain including self, root-most last. */
    public List<ClassInfo> selfAndAncestors() {
        List<ClassInfo> result = new ArrayList<>();
        for (ClassInfo c = this; c != null; c = c.parent) {
            result.add(c);
        }
        return result;
    }
}
