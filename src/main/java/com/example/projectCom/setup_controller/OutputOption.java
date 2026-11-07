package com.example.projectCom.setup_controller;

import lombok.Getter;

@Getter
public enum OutputOption {

    OUTPUT_1("вых.1", 1, (byte) 0x01),   // бит 0
    OUTPUT_2("вых.2", 1, (byte) 0x02),   // бит 1
    OUTPUT_3("вых.3", 1, (byte) 0x04),   // бит 2
    OUTPUT_4("вых.4", 1, (byte) 0x08),   // бит 3
    OUTPUT_5("вых.5", 1, (byte) 0x10),   // бит 4
    OUTPUT_6("вых.6", 1, (byte) 0x20),   // бит 5
    OUTPUT_7("вых.7", 1, (byte) 0x40),   // бит 6
    OUTPUT_8("вых.8", 1, (byte) 0x80),   // бит 7
    OUTPUT_9("вых.9", 2, (byte) 0x01),   // бит 0
    OUTPUT_10("вых.10", 2, (byte) 0x02),   // бит 1
    OUTPUT_11("вых.11", 2, (byte) 0x04),   // бит 2
    OUTPUT_12("вых.12", 2, (byte) 0x08),   // бит 3
    OUTPUT_13("вых.13", 2, (byte) 0x10),   // бит 4
    OUTPUT_14("вых.14", 2, (byte) 0x20),   // бит 5
    OUTPUT_15("вых.15", 2, (byte) 0x40),   // бит 6
    OUTPUT_16("вых.16", 2, (byte) 0x80),   // бит 7
    OUTPUT_17("вых.17", 3, (byte) 0x01),   // бит 0
    OUTPUT_18("вых.18", 3, (byte) 0x02),   // бит 1
    OUTPUT_19("вых.19", 3, (byte) 0x04),   // бит 2
    OUTPUT_20("вых.20", 3, (byte) 0x08),   // бит 3
    OUTPUT_21("вых.21", 3, (byte) 0x10),   // бит 4
    OUTPUT_22("вых.22", 3, (byte) 0x20),   // бит 5
    OUTPUT_23("вых.23", 3, (byte) 0x40),   // бит 6
    OUTPUT_24("вых.24", 3, (byte) 0x80),   // бит 7
    OUTPUT_25("вых.25", 4, (byte) 0x01),   // бит 0
    OUTPUT_26("вых.26", 4, (byte) 0x02),   // бит 1
    OUTPUT_27("вых.27", 4, (byte) 0x04),   // бит 2
    OUTPUT_28("вых.28", 4, (byte) 0x08),   // бит 3
    OUTPUT_29("вых.29", 4, (byte) 0x10),   // бит 4
    OUTPUT_30("вых.30", 4, (byte) 0x20),   // бит 5
    OUTPUT_31("вых.31", 4, (byte) 0x40),   // бит 6
    OUTPUT_32("вых.32", 4, (byte) 0x80),   // бит 7
    OUTPUT_33("вых.33", 5, (byte) 0x01),   // бит 0
    OUTPUT_34("вых.34", 5, (byte) 0x02),   // бит 1
    OUTPUT_35("вых.35", 5, (byte) 0x04),   // бит 2
    OUTPUT_36("вых.36", 5, (byte) 0x08),   // бит 3
    OUTPUT_37("вых.37", 5, (byte) 0x10),   // бит 4
    OUTPUT_38("вых.38", 5, (byte) 0x20),   // бит 5
    OUTPUT_39("вых.39", 5, (byte) 0x40),   // бит 6
    OUTPUT_40("вых.40", 5, (byte) 0x80);   // бит 7
    private final String displayName;
    private final int byteIndex;
    private final byte bitMask;

    OutputOption(String displayName, int byteIndex, byte bitMask) {
        this.displayName = displayName;
        this.byteIndex = byteIndex;
        this.bitMask = bitMask;
    }
    public String maskHex() {
        return String.format("%02X", bitMask);
    }
    }