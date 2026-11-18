package org.example.projectCom.service.panel_synchronisation;

import lombok.RequiredArgsConstructor;
import org.example.projectCom.cache.CachePanelService;
import org.example.projectCom.dto.PanelDto;
import org.example.projectCom.model.Panels;
import org.example.projectCom.service.OutputUtils;
import org.example.projectCom.service.PanelsService;
import org.example.projectCom.service.errors.PanelValidationException;
import org.example.projectCom.setup_controller.OutputOption;
import org.example.projectCom.setup_controller.SyncPeriodOption;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.example.projectCom.setup_controller.PanelNameOption.PANEL_SYNCHRONIZATION;

@Service
@Primary
    @RequiredArgsConstructor
    public class PanelSynchronisationCacheIml implements PanelSynchronisation {
        private final PanelsService panelsService;
        private final CachePanelService cachePanelService;
    @Override
    @Transactional
    public Panels save(Long panelId, int divisor, List<String> outputs) {
        List<OutputOption> selected = OutputUtils.parseSelectedOutputs(outputs);

        // тип панели проверяем по DTO из кэша — без БД
        PanelDto panelDto = cachePanelService.getPanelDtoById(panelId);
        if (panelDto.getNameElement() != PANEL_SYNCHRONIZATION) {
            throw new IllegalArgumentException("Панель " + panelId + " не является панелью синхронизации");
        }
        if (!SyncPeriodOption.isValidDivisor(divisor)) {
            throw new IllegalArgumentException("Недопустимый период: делитель " + divisor);
        }

        // свободные = каналы коммутации − занятое другими детьми;
        // свои выходы в free остаются — синхронизатор может их держать и снимать
        List<OutputOption> free = cachePanelService.listCheckboxPanelId(
                panelDto.getObjectId(), panelId);

        // пустой выбор легален: синхронизатор физически обязателен,
        // выходы подключаются по мере необходимости
        panelsService.assertAllowed(selected, new HashSet<>(free),
                "Выбраны занятые другими панелями выходы");

        // живая сущность для записи — из БД, это правильный путь
        Panels panel = panelsService.getPanelById(panelId);

        // передаём objectId явно, чтобы persist мог сбросить кэш без обращения к panel.getObject()
        return cachePanelService.persist(
                panel,
                OutputUtils.buildSyncPayload(panelDto.getNameElement(), divisor, selected),
                panelDto.getObjectId()
        );
    }
    @Override
        public Long delete(Long panelId) {
            // панель из кэша: тип проверяем без БД
            PanelDto panelDto = cachePanelService.getPanelDtoById(panelId);
            if (panelDto.getNameElement() != PANEL_SYNCHRONIZATION) {
                throw new IllegalArgumentException(
                        "Панель " + panelId + " не является панелью синхронизации");
            }

            // генераторы на объекте — из кэша, без БД
            List<PanelDto> generators = cachePanelService.findPanelsIdObject(panelDto.getObjectId()).stream()
                    .filter(p -> p.getNameElement().isGeneratorType())
                    .toList();
            if (!generators.isEmpty()) {
                throw new PanelValidationException(
                        "Нельзя удалить панель синхронизации: на объекте есть панели-генераторы: " +
                                generators.stream().map(o -> o.getNameElement().getDisplayName())
                                        .collect(Collectors.joining(", ")) +
                                ". Сначала удалите их");
            }

            return cachePanelService.deletePanel(panelId);
        }
    }
