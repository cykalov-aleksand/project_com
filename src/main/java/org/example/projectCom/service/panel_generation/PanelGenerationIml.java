package org.example.projectCom.service.panel_generation;

import org.example.projectCom.model.Panels;
import org.example.projectCom.repository.PanelsRepository;
import org.example.projectCom.service.errors.NoFreeOutputsException;
import org.example.projectCom.service.OutputUtils;
import org.example.projectCom.service.PanelsService;
import org.example.projectCom.setup_controller.OutputOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PanelGenerationIml implements PanelGeneration {
    private final PanelsService panelsService;
    private final PanelsRepository panelsRepository;

    @Transactional
    public Panels save(Long panelId, List<String> outputs) {
        Panels panel = panelsService.getPanelById(panelId);
        if (!panel.getNameElement().isGeneratorType()) {
            throw new IllegalArgumentException("Панель не является панелью генератора");
        }
        List<OutputOption> selected = OutputUtils.parseSelectedOutputs(outputs);
        List<OutputOption> free = panelsService.listCheckboxPanelId(
                panel.getObject().getId(), panelId);
        if (selected.isEmpty() && free.isEmpty()) {
            throw new NoFreeOutputsException(
                    "Конфигурация не сохранена: свободных выходов нет — панель осталась ненастроенной. " +
                            "Освободите канал на другой панели и повторите сохранение");
        }

        panelsService.assertAllowed(selected, new HashSet<>(free),
                "Выбраны занятые другими панелями выходы");
        return panelsService.persist(panel, OutputUtils.buildGeneratorPayload(panel.getNameElement(), selected));
    }

    @Override
    @Transactional
    public Long delete(Long panelId) {
        Panels panel = panelsService.getPanelById(panelId);
        Long objectId = panel.getObject().getId();
        panelsRepository.delete(panel);
        return objectId;
    }
}
