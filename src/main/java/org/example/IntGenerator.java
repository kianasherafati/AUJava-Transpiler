package org.example;

public class IntGenerator {
    private int current = 0;

    public IntGenerator() {
        this.current = 0;
    }

    public IntGenerator(int current) {
        this.current = current;
    }

    public int generate(){
        this.current++;
        return this.current;
    }

    public int getCurrent() {
        return current;
    }

    public void setCurrent(int current) {
        this.current = current;
    }
}
