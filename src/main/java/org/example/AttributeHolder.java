package org.example;

public class AttributeHolder {
    private String code = "";
    private String address = "";
    private String radLangType = "";
    private String cType = "";

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void appendToCurrentCode(String codeToAppend){
        this.code += codeToAppend;
    }

    public String getcType() {
        return cType;
    }

    public void setcType(String cType) {
        this.cType = cType;
    }

    public String getRadLangType() {
        return radLangType;
    }

    public void setRadLangType(String radLangType) {
        this.radLangType = radLangType;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
