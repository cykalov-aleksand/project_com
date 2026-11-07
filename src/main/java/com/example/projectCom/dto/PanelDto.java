package com.example.projectCom.dto;

import com.example.projectCom.setup_controller.PanelNameOption;
import lombok.Data;

@Data
public class PanelDto {
    private Long id;
    private PanelNameOption nameElement;
    private String listCondition;
}