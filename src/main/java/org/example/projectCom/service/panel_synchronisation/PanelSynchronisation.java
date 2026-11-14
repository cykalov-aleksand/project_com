package org.example.projectCom.service.panel_synchronisation;

import org.example.projectCom.model.Panels;

import java.util.List;

public interface PanelSynchronisation {
    Panels save(Long panelId, int divisor, List<String> outputs);
    Long delete(Long panelId);
}
