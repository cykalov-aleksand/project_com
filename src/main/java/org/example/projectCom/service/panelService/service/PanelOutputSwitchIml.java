package org.example.projectCom.service.panelService.service;

import org.example.projectCom.model.Panels;
import org.example.projectCom.repository.PanelsRepository;
import org.example.projectCom.service.OutputUtils;
import org.example.projectCom.service.errors.PanelValidationException;
import org.example.projectCom.service.panelService.PanelOutputSwitch;
import org.example.projectCom.setup_controller.OutputOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.example.projectCom.service.panelService.service.PanelsService.SWITCHING_PAYLOAD_SIZE;
import static org.example.projectCom.setup_controller.PanelNameOption.PANEL_OUTPUT_SWITCHING;

@Service
@RequiredArgsConstructor
public class PanelOutputSwitchIml implements PanelOutputSwitch {
    private final PanelsService panelsService;
    private final PanelsRepository panelsRepository;
    @Transactional
    public Panels save(Long panelId, Long objectId, List<String> outputs) {
        Panels panel = panelsService.getPanelById(panelId);
        if (panel.getNameElement() != PANEL_OUTPUT_SWITCHING) {
            throw new IllegalArgumentException("Панель " + panelId + " не является панелью коммутации");
        }
        if (!panel.getObject().getId().equals(objectId)) {
            throw new IllegalArgumentException(
                    "Панель " + panelId + " не относится к объекту " + objectId);
        }

        Set<OutputOption> selected = new HashSet<>(OutputUtils.parseSelectedOutputs(outputs));

// каналы, занятые детьми, коммутация держит всегда: добавляем их к выбору,
// независимо от того, что прислала форма — инвариант домена, а не правка ввода
        Set<OutputOption> taken = panelsService.takenByOthers(panelId);
        selected.addAll(taken);

        if (selected.isEmpty()) {
            throw new PanelValidationException(
                    "Панель коммутации должна иметь хотя бы один выход");
        }

// защита сохраняется: если снятое всё же пересекается с занятым,
// значит, пришёл некорректный запрос — но через обычный флоу это недостижимо
        Set<OutputOption> removed = new HashSet<>(
                OutputUtils.extractSelectedOutputs(panel.getOutputsJsonString()));
        removed.removeAll(selected);

        if (!removed.isEmpty()) {
            Set<OutputOption> blocked = new HashSet<>(panelsService.takenByOthers(panelId));
            blocked.retainAll(removed);
            if (!blocked.isEmpty()) {
                throw new PanelValidationException(
                        "Нельзя освободить каналы, занятые другими панелями: " +
                                blocked.stream().map(OutputOption::getDisplayName)
                                        .collect(Collectors.joining(", ")));
            }
        }

        return panelsService.persist(panel,
                OutputUtils.buildPayload(panel.getNameElement(),
                        OutputUtils.packOutputs(new ArrayList<>(selected), SWITCHING_PAYLOAD_SIZE)));
    }

    @Override
    @Transactional
    public Long delete(Long panelId) {
        Panels panel = panelsService.getPanelById(panelId);

        Set<OutputOption> taken = panelsService.takenByOthers(panelId);
        if (!taken.isEmpty()) {
            throw new PanelValidationException(
                    "Нельзя удалить панель коммутации: её каналы заняты другими панелями: " +
                            taken.stream().map(OutputOption::getDisplayName)
                                    .collect(Collectors.joining(", ")));
        }

        Long objectId = panel.getObject().getId();
        panelsRepository.delete(panel);
        return objectId;
    }
}