package org.example.projectCom.service.errors;

public class PanelValidationException extends NoFreeOutputsException {
    public PanelValidationException(String message) {
        super(message);
    }
}
