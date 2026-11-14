package org.example.projectCom.service.panel_generation;

import org.example.projectCom.model.Panels;

import java.util.List;

public interface PanelGeneration {
    Panels save(Long panelId, List<String> outputs);
    Long delete(Long panelId);
}
