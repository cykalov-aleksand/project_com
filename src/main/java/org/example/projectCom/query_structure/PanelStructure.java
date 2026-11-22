package org.example.projectCom.query_structure;

import org.example.projectCom.dto.enum_panels.PanelNameOption;


import java.util.List;


public enum PanelStructure {
    SWITCHING(new MessageStructure("Панель коммутации", List.of(
            new FildSpec("key", FildType.UNIT8, 1),
            new FildSpec("outputs", FildType.OUTPUTS_BITMASK, 5)))),
    SYNCHRONIZATION(new MessageStructure("Панель синхронизации", List.of(
            new FildSpec("key", FildType.UNIT8, 1),
            new FildSpec("outputs", FildType.OUTPUTS_BITMASK, 5),
            new FildSpec("divisor", FildType.UNIT16, 2)))),
    GENERATOR(new MessageStructure("Панель генератора", List.of(
            new FildSpec("key", FildType.UNIT8, 1),
            new FildSpec("outputs", FildType.OUTPUTS_BITMASK, 5))));
    private final MessageStructure structure;

    PanelStructure(MessageStructure structure) {
        this.structure = structure;
    }

    public MessageStructure structure() {
        return structure;
    }

    public static PanelStructure of(PanelNameOption name) {
        return switch (name) {
            case PANEL_OUTPUT_SWITCHING -> SWITCHING;
            case PANEL_SYNCHRONIZATION -> SYNCHRONIZATION;
            default -> GENERATOR;
        };
    }
}

