package org.example.projectCom.dto;

import org.example.projectCom.setup_controller.PanelNameOption;
import lombok.Data;

@Data
public class PanelDto {
    private Long id;
    private PanelNameOption nameElement;
    private String listCondition;
}