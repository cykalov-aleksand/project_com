package org.example.projectCom.service.errors;

public class SerialPortException extends RuntimeException{
    public SerialPortException(String message){
        super(message);
    }
}
