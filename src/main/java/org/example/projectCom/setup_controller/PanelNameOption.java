package org.example.projectCom.setup_controller;

import lombok.Getter;

import java.util.List;

@Getter
public enum PanelNameOption {
    PANEL_OUTPUT_SWITCHING((byte) 0x01, "Панель коммутации выходов", false),
    PANEL_SYNCHRONIZATION((byte) 0x02, "Панель синхронизации", false),
    PANEL_GENERATION_1((byte) 0x03, "Панель генератора 1", true),
    PANEL_GENERATION_2((byte) 0x04, "Панель генератора 2", true),
    PANEL_GENERATION_3((byte) 0x05, "Панель генератора 3", true),
    PANEL_GENERATION_4((byte) 0x06, "Панель генератора 4", true),
    PANEL_GENERATION_5((byte) 0x07, "Панель генератора 5", true),
    PANEL_GENERATION_6((byte) 0x08, "Панель генератора 6", true),
    PANEL_GENERATION_7((byte) 0x09, "Панель генератора 7", true),
    PANEL_GENERATION_8((byte) 0x0A, "Панель генератора 8", true),
    PANEL_GENERATION_9((byte) 0x0B, "Панель генератора 9", true),
    PANEL_GENERATION_10((byte) 0x0C, "Панель генератора 10", true);

    private final byte keyCode;
    private final String displayName;
    private final boolean generator;   // ← новое поле

    PanelNameOption(byte keyCode, String displayName, boolean generator) {
        this.keyCode = keyCode;
        this.displayName = displayName;
        this.generator = generator;
    }

    public boolean isGeneratorType() {
        return generator;
    }
}