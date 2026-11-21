package org.example.projectCom.service.panelService.service;

import org.example.projectCom.dto.PanelDto;
import org.example.projectCom.mapper.PanelsMapperDto;
import org.example.projectCom.model.EquipmentObject;
import org.example.projectCom.model.Panels;
import org.example.projectCom.repository.ObjectRepository;
import org.example.projectCom.repository.PanelsRepository;
import org.example.projectCom.service.OutputUtils;
import org.example.projectCom.service.panelService.PanelOccupancyLogic;
import org.example.projectCom.setup_controller.OutputOption;
import org.example.projectCom.setup_controller.PanelNameOption;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static org.example.projectCom.setup_controller.PanelNameOption.PANEL_OUTPUT_SWITCHING;
import static org.example.projectCom.setup_controller.PanelNameOption.PANEL_SYNCHRONIZATION;

@Service
@RequiredArgsConstructor
public class PanelsService {
    private final PanelsRepository panelsRepository;
    private final ObjectRepository objectRepository;
    private final PanelsMapperDto panelsMapperDto;

    public static final int SWITCHING_PAYLOAD_SIZE = 5;
    @Transactional(readOnly = true)
    public List<PanelDto> findPanelsIdObject(Long objectId) {
        List<Panels> panelsList = panelsRepository.findPanels(objectId);
        return panelsList.stream().map(panelsMapperDto::toDto).toList();
    }
    //метод создания панели в БД
    @Transactional
    public Panels createPanel(Long objectId, PanelNameOption panelNameOption) {
        if (existsPanelType(objectId, panelNameOption)) {
            throw new IllegalArgumentException(
                    "Панель типа " + panelNameOption.getDisplayName() + " уже есть у объекта " + objectId);
        }
        EquipmentObject object = objectRepository.findById(objectId)
                .orElseThrow(() -> new EntityNotFoundException("Объект не найден: " + objectId));

        Panels panel = new Panels();
        panel.setNameElement(panelNameOption);
        panel.setObject(object);
        return panelsRepository.save(panel);
        //return panel;
    }
    //поиск панели по Id
    public Panels getPanelById(Long id) {
        return panelsRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Панель с ID " + id + " не найдена"));
    }
    //метод создания списка имен панелей для исследуемого id объекта с последующим выводом его в комбобокс
    //с целью отслеживания какие панели на данный момент присутствуют в указанном объекте и принятия решения какие панели доступны для создания
    public List<PanelNameOption> getAvailablePanelTypes(Long objectId) {
        List<Panels> existing = panelsRepository.findPanels(objectId);
        Set<PanelNameOption> existingTypes = existing.stream()
                .map(Panels::getNameElement).collect(Collectors.toSet());
        List<PanelNameOption> allTypes = List.of(PanelNameOption.values());
        // Убираем уже созданные
        List<PanelNameOption> available = allTypes.stream()
                .filter(type -> !existingTypes.contains(type)).toList();
        if (!existingTypes.contains(PANEL_OUTPUT_SWITCHING)) {
            // Пока нет панели коммутации — можно создавать только её
            return List.of(PANEL_OUTPUT_SWITCHING);
        }
        if (!existingTypes.contains(PANEL_SYNCHRONIZATION)) {
            // Панель коммутации есть, синхронизации нет — можно только её
            return List.of(PANEL_SYNCHRONIZATION);
        }
        return available;
    }
    // метод проверки наличие панели для исследуемого объекта
    public boolean existsPanelType(Long objectId, PanelNameOption type) {
        return panelsRepository.findPanels(objectId).stream()
                .map(Panels::getNameElement)
                .anyMatch(o -> o.equals(type));
    }
    //создание мапы содержащей количество элементов на выводимой панели
    public Map<Integer, List<OutputOption>> groupOfPointersWeekends(PanelNameOption panelNameOption, Long objectId, Long panelId) {
        Map<Integer, List<OutputOption>> outputGroups = new LinkedHashMap<>();
        for (OutputOption option : OutputOption.values()) {
            outputGroups.computeIfAbsent(option.getByteIndex(), k -> new ArrayList<>()).add(option);
        }
        if (panelNameOption == PANEL_OUTPUT_SWITCHING) {
            return outputGroups;
        }
        if (PANEL_SYNCHRONIZATION == panelNameOption) {
            return OutputUtils.buildFixedGrid(OutputUtils.extractSelectedOutputs(
                    getPanelJson(objectId, PANEL_OUTPUT_SWITCHING),
                    PANEL_OUTPUT_SWITCHING));
        }
        return OutputUtils.buildFixedGrid(listCheckboxPanelId(objectId, panelId));
    }
    //создания списка свободных элементов указанной панели, по заданному объекту
    public List<OutputOption> listCheckboxPanelId(Long objectId, Long excludePanelId) {
        List<Panels> panels = panelsRepository.findPanels(objectId);
        return PanelOccupancyLogic.calculateFromEntities(panels, excludePanelId).free();
    }
    // чтение строки JSON по указанному объекту с заданным типом панели
    @Transactional(readOnly = true)
    public String getPanelJson(Long objectId, PanelNameOption type) {
        return panelsRepository.findPanels(objectId).stream()
                .filter(p -> p.getNameElement().equals(type))
                .map(Panels::getOutputsJsonString)
                .findFirst()
                .orElse(null);
    }
    // создание мапы с последующим ее использованием для формирования списка элементов на панели
    public Map<Integer, List<OutputOption>> buildOutputGrid(Long idPanel) {
        Panels panel = getPanelById(idPanel);
        if (panel.getNameElement() == PANEL_OUTPUT_SWITCHING) {
            return OutputUtils.buildFixedGrid(List.of(OutputOption.values()));
        }
        List<OutputOption> selected = OutputUtils.extractSelectedOutputs(
                panel.getOutputsJsonString(),
                OutputUtils.payloadOffset(panel.getNameElement()));
        List<OutputOption> free = listCheckboxPanelId(
                panel.getObject().getId(),idPanel);
        List<OutputOption> visible = new ArrayList<>(selected);
        for (OutputOption element : free) {
            if (!visible.contains(element)) visible.add(element);
        }
        return OutputUtils.buildFixedGrid(visible);
    }
    //создания сет выходов занятых в дочерних панелях, свои выходы не блокируются
    public Set<OutputOption> takenByOthers(Long idPanel) {
        Panels panel = getPanelById(idPanel);
        List<Panels> allPanels = panelsRepository.findPanels(panel.getObject().getId());
        Set<OutputOption> occupied = PanelOccupancyLogic.calculateFromEntities(allPanels, idPanel).occupied();

        // Если это панель коммутации — она не вычитает свои выходы.
        if (panel.getNameElement() == PanelNameOption.PANEL_OUTPUT_SWITCHING) {
            return occupied;
        }

        // Для дочерних панелей: вычитаем свои из занятых
        int offset = OutputUtils.payloadOffset(panel.getNameElement());
        Set<OutputOption> mine = new HashSet<>(
                OutputUtils.extractSelectedOutputs(panel.getOutputsJsonString(), offset));
        occupied.removeAll(mine);
        return occupied;
    }
    //запись строки JSON в объект базы данных
    public Panels persist(Panels panel, byte[] payload) {
        panel.setOutputsJsonString(OutputUtils.toJsonbHex(payload));
        return panelsRepository.save(panel);
    }
    public void assertAllowed(List<OutputOption> selected,
                              @NonNull Set<OutputOption> allowed,
                              String errorMessage) {
        if (!allowed.containsAll(selected)) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
    @Transactional
    public Long deletePanel(Long panelId) {
        Panels panel = panelsRepository.findById(panelId)
                .orElseThrow(() -> new EntityNotFoundException("Панель с ID " + panelId + " не найдена"));
        Long objectId = panel.getObject().getId();
        panelsRepository.delete(panel);
        return objectId;
    }
}
