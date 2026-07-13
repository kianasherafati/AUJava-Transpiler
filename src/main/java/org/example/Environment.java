package org.example;

import java.util.Map;
import java.util.TreeMap;

public class Environment {
    private final Map<String, Symbol> table;
    private final Environment parent;

    public Environment() {
        this.table = new TreeMap<>();
        this.parent = null;
    }

    public Environment(Environment parent){
        this.table = new TreeMap<>();
        this.parent = parent;
    }

    public Environment(Environment parent, Map<String, Symbol> table){
        this.table = table;
        this.parent = parent;
    }

    public boolean containsSymbolWithName(String name){
        return this.table.containsKey(name);
    }

    public Symbol getSymbol(String name){
        for(Environment environment = this; environment != null; environment = environment.parent){
            Symbol found = (environment.table.get(name));
            if(found != null){
                return found;
            }
        }
        return null;
    }

    public void putSymbol(Symbol symbol){
        this.table.put(symbol.getName(), symbol);
    }

    public Environment getParent(){
        return this.parent;
    }
}
