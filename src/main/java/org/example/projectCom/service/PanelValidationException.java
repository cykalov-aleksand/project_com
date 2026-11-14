package org.example.projectCom.service;

public class PanelValidationException extends NoFreeOutputsException{
    public PanelValidationException(String message) {
        super(message);
    }
}
