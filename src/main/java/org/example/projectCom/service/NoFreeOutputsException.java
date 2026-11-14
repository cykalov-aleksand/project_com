package org.example.projectCom.service;

public class NoFreeOutputsException extends RuntimeException{
    public NoFreeOutputsException(String message) {
        super(message);
    }
}
