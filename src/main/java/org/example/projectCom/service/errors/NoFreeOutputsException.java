package org.example.projectCom.service.errors;

public class NoFreeOutputsException extends RuntimeException {
    public NoFreeOutputsException(String message) {
        super(message);
    }
}
