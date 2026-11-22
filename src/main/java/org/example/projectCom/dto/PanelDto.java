package org.example.projectCom.dto;

import lombok.Data;
import org.example.projectCom.dto.enum_panels.PanelNameOption;

@Data
public class PanelDto {
    private Long id;
    private PanelNameOption nameElement;
    private String listCondition;
    private Long objectId;
}