package org.example.projectCom.service.panel_generation;

import lombok.RequiredArgsConstructor;
import org.example.projectCom.cache.CachePanelService;
import org.example.projectCom.dto.PanelDto;
import org.example.projectCom.model.Panels;
import org.example.projectCom.repository.PanelsRepository;
import org.example.projectCom.service.OutputUtils;
import org.example.projectCom.service.PanelsService;
import org.example.projectCom.service.errors.NoFreeOutputsException;
import org.example.projectCom.setup_controller.OutputOption;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
    @RequiredArgsConstructor
    public class PanelGenerationCacheIml implements PanelGeneration {
        private final PanelsService panelsService;
        private final CachePanelService cachePanelService;
        private final PanelsRepository panelsRepository; // останется только для delete, см. ниже

    @Transactional
    public Panels save(Long panelId, List<String> outputs) {
        // валидация типа панели — по DTO из кэша, без похода в БД
        PanelDto panelDto = cachePanelService.getPanelDtoById(panelId);
        if (!panelDto.getNameElement().isGeneratorType()) {
            throw new IllegalArgumentException("Панель не является панелью генератора");
        }

        List<OutputOption> selected = OutputUtils.parseSelectedOutputs(outputs);
        List<OutputOption> free = cachePanelService.listCheckboxPanelId(
                panelDto.getObjectId(), panelId);

        if (selected.isEmpty() && free.isEmpty()) {
            throw new NoFreeOutputsException(
                    "Конфигурация не сохранена: свободных выходов нет — панель осталась ненастроенной. " +
                            "Освободите канал на другой панели и повторите сохранение");
        }

        panelsService.assertAllowed(selected, new HashSet<>(free),
                "Выбраны занятые другими панелями выходы");

        // живая сущность для записи — только из БД
        Panels panel = panelsService.getPanelById(panelId);

        // objectId передаём явно: persist сбросит кэш по нему, не трогая ленивую связь
        return cachePanelService.persist(panel,
                OutputUtils.buildGeneratorPayload(panelDto.getNameElement(), selected),
                panelDto.getObjectId());
    }

    @Override
    @Transactional
    public Long delete(Long panelId) {
        return cachePanelService.deletePanel(panelId);
    }
    }

