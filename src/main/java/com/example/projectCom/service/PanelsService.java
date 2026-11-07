package com.example.projectCom.service;

import com.example.projectCom.dto.PanelDto;
import com.example.projectCom.mapper.PanelsMapperDto;
import com.example.projectCom.model.EquipmentObject;
import com.example.projectCom.model.Panels;
import com.example.projectCom.repository.ObjectRepository;
import com.example.projectCom.repository.PanelsRepository;
import com.example.projectCom.setup_controller.OutputOption;
import com.example.projectCom.setup_controller.PanelNameOption;
import com.example.projectCom.setup_controller.SyncPeriodOption;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.example.projectCom.setup_controller.PanelNameOption.PANEL_OUTPUT_SWITCHING;
import static com.example.projectCom.setup_controller.PanelNameOption.PANEL_SYNCHRONIZATION;

@Service
@RequiredArgsConstructor
public class PanelsService {
    private final PanelsRepository panelsRepository;
    private final ObjectRepository objectRepository;
    private final PanelsMapperDto panelsMapperDto;


    public List<PanelDto> findPanelsIdObject(Long objectId) {
        List<Panels> panelsList = panelsRepository.findPanels(objectId);
        return panelsList.stream().map(panelsMapperDto::toDto).toList();
    }

    public PanelDto savePanel(PanelDto dto, Long objectId) {
        List<Panels> existing = panelsRepository.findPanels(objectId);

        boolean alreadyExists = existing.stream()
                .anyMatch(p -> p.getNameElement() == dto.getNameElement());

        if (alreadyExists) {
            // Панель этого типа уже есть — обновляем её вместо создания новой
            Panels existingPanel = existing.stream()
                    .filter(p -> p.getNameElement() == dto.getNameElement())
                    .findFirst()
                    .orElseThrow();
            existingPanel.setOutputsJsonString(dto.getListCondition());
            return panelsMapperDto.toDto(panelsRepository.save(existingPanel));
        }
        Panels panel = panelsMapperDto.toEntity(dto);
        panel.setObject(objectRepository.getReferenceById(objectId));
        return panelsMapperDto.toDto(panelsRepository.save(panel));
    }

    @Transactional
    public Panels createPanel(Long objectId, PanelNameOption panelNameOption) {
        EquipmentObject object = objectRepository.findById(objectId)
                .orElseThrow(() -> new EntityNotFoundException("Объект не найден: " + objectId));

        Panels panel = new Panels();
        panel.setNameElement(panelNameOption);
        panel.setObject(object);
        return panelsRepository.save(panel);
    }

    @Transactional
    public Panels saveOutputConfig(Long panelId, EquipmentObject equipmentObject, byte[] packedMask) {
        Panels panel = panelsRepository.findById(panelId)
                .orElseThrow(() -> new EntityNotFoundException("Панель не найдена: " + panelId));

        // Проверка согласованности: панель должна принадлежать этому объекту
        if (!panel.getObject().getId().equals(equipmentObject.getId())) {
            throw new IllegalArgumentException(
                    "Панель " + panelId + " не относится к объекту " + equipmentObject.getId());
        }

        // Собираем полный пакет: байт-ключ панели + маска выходов
        byte[] payload = OutputUtils.buildPayload(panel.getNameElement(), packedMask);
        panel.setOutputsJsonString(OutputUtils.toJsonbHex(payload)); // "010108000000"
        return panelsRepository.save(panel);
    }

    public Panels getPanelById(Long id) {
        return panelsRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Панель с ID " + id + " не найдена"));
    }

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

    public boolean existsPanelType(Long objectId, PanelNameOption type) {
        return panelsRepository.findPanels(objectId).stream()
                .map(Panels::getNameElement)
                .anyMatch(o -> o.equals(type));
    }

    public Map<Integer, List<OutputOption>> groupOfPointersWeekends(PanelNameOption panelNameOption, Long objectId) {
        Map<Integer, List<OutputOption>> outputGroups = new LinkedHashMap<>();
        for (OutputOption option : OutputOption.values()) {
            outputGroups.computeIfAbsent(option.getByteIndex(), k -> new ArrayList<>()).add(option);
        }
        if (panelNameOption == PANEL_OUTPUT_SWITCHING) {
            return outputGroups;
        }
        if (PANEL_SYNCHRONIZATION == panelNameOption) {
           return OutputUtils.buildFixedGrid(OutputUtils.extractSelectedOutputs(getPanelJson(objectId, PANEL_OUTPUT_SWITCHING)));
        }
        return outputGroups;
    }

    public void delete(Long id) {
        panelsRepository.deleteById(id);
    }
    @Transactional(readOnly = true)
    public String getPanelJson(Long objectId, PanelNameOption type) {
        return panelsRepository.findPanels(objectId).stream()
                .filter(p -> p.getNameElement() == type)
                .map(Panels::getOutputsJsonString)
                .findFirst()
                .orElse(null);
    }
    @Transactional
    public Panels saveSyncConfig(Long panelId, Long objectId, int divisor,
                                 List<OutputOption> selected) {
        if (!SyncPeriodOption.isValidDivisor(divisor)) {
            throw new IllegalArgumentException("Недопустимый период: делитель " + divisor);
        }
        Panels panel = panelsRepository.findById(panelId)
                .orElseThrow(() -> new EntityNotFoundException("Панель не найдена: " + panelId));

        Set<OutputOption> allowed = new HashSet<>(OutputUtils.extractSelectedOutputs(
                getPanelJson(objectId, PANEL_OUTPUT_SWITCHING)));
        if (!allowed.containsAll(selected)) {
            throw new IllegalArgumentException("Выбраны выходы, отсутствующие на панели коммутации");
        }

        byte[] payload = OutputUtils.buildSyncPayload(panel.getNameElement(), divisor, selected);
        panel.setOutputsJsonString(OutputUtils.toJsonbHex(payload));
        return panelsRepository.save(panel);
    }
}
