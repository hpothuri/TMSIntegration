package com.dbms.tmsint.pojo;

public class DataLine {
    public DataLine() {
        super();
    }
    
    public DataLine(String url,String text) {
        super();
        this.url = url;
        this.text = text;       
    }
    
    
    private String url;
    private String text;

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
