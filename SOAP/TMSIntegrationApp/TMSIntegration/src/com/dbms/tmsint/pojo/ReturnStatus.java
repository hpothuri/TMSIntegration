package com.dbms.tmsint.pojo;

public class ReturnStatus {
    public ReturnStatus() {
        super();
    }
    
    public static final String SUCCESS = "SUCCESS";
    public static final String FAIL = "FAIL";
    
    private String status;
    private String errorCode;
    private String errorMessage;

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
