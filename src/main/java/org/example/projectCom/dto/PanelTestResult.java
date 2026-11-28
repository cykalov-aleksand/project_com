package org.example.projectCom.dto;

public record PanelTestResult(boolean ok, String text) {
    public static PanelTestResult ok(String text) {
        return new PanelTestResult(true, text);
    }

    public static PanelTestResult fail(String text) {
        return new PanelTestResult(false, text);
    }
}