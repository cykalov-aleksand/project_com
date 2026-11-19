package org.example.projectCom.service.panelService.service;

import org.example.projectCom.model.Panels;
import org.example.projectCom.repository.PanelsRepository;
import org.example.projectCom.service.OutputUtils;
import org.example.projectCom.service.errors.PanelValidationException;
import org.example.projectCom.service.panelService.PanelSynchronisation;
import org.example.projectCom.setup_controller.OutputOption;
import org.example.projectCom.setup_controller.SyncPeriodOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.example.projectCom.setup_controller.PanelNameOption.PANEL_SYNCHRONIZATION;

@Service
@RequiredArgsConstructor
public class PanelSynchronisationIml implements PanelSynchronisation {
    private final PanelsService panelsService;
    private final PanelsRepository panelsRepository;
    @Transactional
    public Panels save(Long panelId, int divisor, List<String> outputs) {
        List<OutputOption> selected = OutputUtils.parseSelectedOutputs(outputs);
        Panels panel = panelsService.getPanelById(panelId);
        if (panel.getNameElement() != PANEL_SYNCHRONIZATION) {
            throw new IllegalArgumentException("Панель " + panelId + " не является панелью синхронизации");
        }
        if (!SyncPeriodOption.isValidDivisor(divisor)) {
            throw new IllegalArgumentException("Недопустимый период: делитель " + divisor);
        }

        // свободные = каналы коммутации − занятое другими детьми;
        // свои выходы в free остаются — синхронизатор может их держать и снимать
        List<OutputOption> free = panelsService.listCheckboxPanelId(
                panel.getObject().getId(), panelId);

        // пустой выбор легален: синхронизатор физически обязателен,
        // выходы подключаются по мере необходимости
        panelsService.assertAllowed(selected, new HashSet<>(free),
                "Выбраны занятые другими панелями выходы");

        return panelsService.persist(panel,
                OutputUtils.buildSyncPayload(panel.getNameElement(), divisor, selected));
    }
    @Transactional
    public Long delete(Long panelId) {
        Panels panel = panelsService.getPanelById(panelId);
        if (panel.getNameElement() != PANEL_SYNCHRONIZATION) {
            throw new IllegalArgumentException(
                    "Панель " + panelId + " не является панелью синхронизации");
        }

        Long objectId = panel.getObject().getId();
        List<Panels> generators = panelsRepository.findPanels(objectId).stream()
                .filter(p -> p.getNameElement().isGeneratorType())
                .toList();
        if (!generators.isEmpty()) {
            throw new PanelValidationException(
                    "Нельзя удалить панель синхронизации: на объекте есть панели-генераторы: " +
                            generators.stream().map(o->o.getNameElement().getDisplayName())
                                    .collect(Collectors.joining(", ")) +
                            ". Сначала удалите их");
        }

        panelsRepository.delete(panel);
        return objectId;
    }
}
