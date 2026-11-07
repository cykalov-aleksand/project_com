package com.example.projectCom.setup_controller;

import lombok.Getter;

import java.util.List;


@Getter
public enum PanelNameOption {
    PANEL_OUTPUT_SWITCHING((byte) 0x01, "Панель коммутации выходов"),
    PANEL_SYNCHRONIZATION((byte) 0x02, "Панель синхронизации"),
    PANEL_GENERATION_1((byte) 0x03, "Панель генератора 1"),
    PANEL_GENERATION_2((byte) 0x04, "Панель генератора 2"),
    PANEL_GENERATION_3((byte) 0x05, "Панель генератора 3"),
    PANEL_GENERATION_4((byte) 0x06, "Панель генератора 4"),
    PANEL_GENERATION_5((byte) 0x07, "Панель генератора 5"),
    PANEL_GENERATION_6((byte) 0x08, "Панель генератора 6"),
    PANEL_GENERATION_7((byte) 0x09, "Панель генератора 7"),
    PANEL_GENERATION_8((byte) 0x0A, "Панель генератора 8"),
    PANEL_GENERATION_9((byte) 0x0B, "Панель генератора 9"),
    PANEL_GENERATION_10((byte) 0x0C, "Панель генератора 10");

    private final byte keyCode;
    private final String displayName;

    PanelNameOption(byte keyCode, String displayName) {
        this.keyCode = keyCode;
        this.displayName = displayName;
    }
    public static byte[] packOutputs(List<OutputOption> selected, int byteCount) {
        byte[] result = new byte[byteCount];
        for (OutputOption option : selected) {
            result[option.getByteIndex()] |= option.getBitMask();
        }
        return result;
    }
    }
