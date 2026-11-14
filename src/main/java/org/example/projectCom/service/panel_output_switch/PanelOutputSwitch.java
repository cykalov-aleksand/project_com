package org.example.projectCom.service.panel_output_switch;

import org.example.projectCom.model.Panels;

import java.util.List;

public interface PanelOutputSwitch {
    Panels save(Long panelId, Long objectId, List<String> outputs);
    Long delete(Long panelId);
}
