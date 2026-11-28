package org.example.projectCom.service.panelService.cacheServicePanel;

import org.example.projectCom.dto.PanelDto;
import org.example.projectCom.dto.enum_panels.OutputOption;
import org.example.projectCom.dto.enum_panels.PanelNameOption;
import org.example.projectCom.model.Panels;
import org.example.projectCom.service.OutputUtils;
import org.example.projectCom.service.panelService.PanelOccupancyLogic;
import org.example.projectCom.service.panelService.myCache.HwCache;
import org.example.projectCom.service.panelService.myCache.MyCache;
import org.example.projectCom.service.panelService.service.PanelsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.example.projectCom.dto.enum_panels.PanelNameOption.PANEL_OUTPUT_SWITCHING;
import static org.example.projectCom.dto.enum_panels.PanelNameOption.PANEL_SYNCHRONIZATION;

@Component
    public class PanelServiceCache {
        private static final Logger logger = LoggerFactory.getLogger(PanelServiceCache.class);

        private final PanelsService panelsService;

        // кэш: ключ — objectId, значение — список DTO панелей объекта.
        // Записей может быть несколько (по одному на каждый объект в работе),
        // сброс — при записи в соответствующий объект
        private final HwCache<Long, List<PanelDto>> panelsDtoCache = new MyCache<>();

        public PanelServiceCache(PanelsService panelsService) {
            this.panelsService = panelsService;
        }

        // ==================== ОСНОВА КЭША ====================

        /**
         * Список панелей объекта из кэша (или из БД при промахе).
         */
        public List<PanelDto> findPanelsIdObject(Long objectId) {
            List<PanelDto> cached = panelsDtoCache.get(objectId);
            if (cached != null) {
                logger.info("чтение информации с кэша - {}", cached);
                return cached;
            }
            logger.info("чтение информации с базы данных");
            List<PanelDto> fresh = panelsService.findPanelsIdObject(objectId);
            panelsDtoCache.put(objectId, fresh);
            logger.info("запись информации в кэш");
            return fresh;
        }

        /**
         * Поиск панели по id: сначала во всех записях кэша, при промахе — БД.
         */
        public PanelDto getPanelDtoById(Long id) {
            // 1. ищем во всех закэшированных списках — у кого-то из пользователей
            //    объект этой панели мог быть уже подгружен
            PanelDto found = panelsDtoCache.getAll().stream()
                    .flatMap(List::stream)
                    .filter(p -> p.getId().equals(id))
                    .findFirst()
                    .orElse(null);
            if (found != null) {
                logger.info("панель {} найдена в кэше", id);
                return found;
            }

            // 2. промах — читаем из БД, узнаём objectId и подгружаем список объекта
            logger.info("панель {} читается с базы данных", id);
            Panels panel = panelsService.getPanelById(id);

            Long objectId = panel.getObject().getId();

            // 3. подгружаем список панелей объекта в кэш и берём нужную из него
            List<PanelDto> panels = findPanelsIdObject(objectId);
            return panels.stream()
                    .filter(p -> p.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Панель " + id + " не найдена в списке объекта " + objectId));
        }

        // ==================== ЗАПИСЬ ====================

        /**
         * Создание панели: делегируем в сервис, после создания — сброс кэша объекта.
         */
        public Panels createPanel(Long objectId, PanelNameOption type) {
            Panels created = panelsService.createPanel(objectId, type);
            panelsDtoCache.remove(objectId);
            logger.info("кэш сброшен после создания панели {} типа {}", created.getId(), type);
            return created;
        }

        /**
         * Запись JSON в панель: делегируем в сервис, после записи — сброс кэша объекта.
         */
        public Panels persist(Panels panel, byte[] payload, Long objectId) {
            Panels saved = panelsService.persist(panel, payload);
            panelsDtoCache.remove(objectId);
            logger.info("кэш сброшен для объекта {} после записи панели {}", objectId, saved.getId());
            return saved;
        }
        // ==================== ТИПЫ И СЕТКИ ====================

        /**
         * Доступные для создания типы панелей — из кэша.
         */
        public List<PanelNameOption> getAvailablePanelTypes(Long objectId) {
            Set<PanelNameOption> existingTypes = findPanelsIdObject(objectId).stream()
                    .map(PanelDto::getNameElement)
                    .collect(Collectors.toSet());

            if (!existingTypes.contains(PANEL_OUTPUT_SWITCHING)) {
                // Пока нет панели коммутации — можно создавать только её
                return List.of(PANEL_OUTPUT_SWITCHING);
            }
            if (!existingTypes.contains(PANEL_SYNCHRONIZATION)) {
                // Панель коммутации есть, синхронизации нет — можно только её
                return List.of(PANEL_SYNCHRONIZATION);
            }
            return Stream.of(PanelNameOption.values())
                    .filter(type -> !existingTypes.contains(type))
                    .toList();
        }

        /**
         * Payload панели заданного типа на объекте — из кэша.
         */
        public String getPanelJson(Long objectId, PanelNameOption type) {
            return findPanelsIdObject(objectId).stream()
                    .filter(p -> p.getNameElement() == type)
                    .map(PanelDto::getListCondition)
                    .findFirst()
                    .orElse(null);
        }


    /**
     * Список свободных элементов указанной панели по заданному объекту — из кэша.
     */

public List<OutputOption> listCheckboxPanelId(Long objectId, Long excludePanelId) {
List<PanelDto> panels = findPanelsIdObject(objectId);
return PanelOccupancyLogic.calculateFromDtos(panels, excludePanelId).free();
}/**

        /**
         * Чистая логика построения сетки — кэш подтягивается внутри цепочки.
         */
        public Map<Integer, List<OutputOption>> groupOfPointersWeekends(
                PanelNameOption panelNameOption, Long objectId, Long panelId) {
            Map<Integer, List<OutputOption>> outputGroups = new LinkedHashMap<>();
            for (OutputOption option : OutputOption.values()) {
                outputGroups.computeIfAbsent(option.getByteIndex(), k -> new ArrayList<>()).add(option);
            }
            if (panelNameOption == PANEL_OUTPUT_SWITCHING) {
                return outputGroups;
            }
            if (panelNameOption == PANEL_SYNCHRONIZATION) {
                return OutputUtils.buildFixedGrid(
                        OutputUtils.extractSelectedOutputs(
                                getPanelJson(objectId, PANEL_OUTPUT_SWITCHING), PANEL_OUTPUT_SWITCHING));
            }
            return OutputUtils.buildFixedGrid(listCheckboxPanelId(objectId, panelId));
        }

        /**
         * Выходы, занятые другими панелями объекта — из кэша.
         */
        public Set<OutputOption> takenByOthers(Long idPanel) {
            PanelDto panel = getPanelDtoById(idPanel);
            List<PanelDto> allPanels = findPanelsIdObject(panel.getObjectId());
            //Set<OutputOption> occupied = PanelOccupancyLogic.calculateFromDtos(allPanels, idPanel).occupied();
            Set<OutputOption> occupied = new HashSet<>(
                    PanelOccupancyLogic.calculateFromDtos(allPanels, idPanel).occupied());
            if (panel.getNameElement() == PANEL_OUTPUT_SWITCHING) {
                return occupied;
            }

            int offset = OutputUtils.payloadOffset(panel.getNameElement());
            Set<OutputOption> mine = new HashSet<>(
                    OutputUtils.extractSelectedOutputs(panel.getListCondition(), offset));
            occupied.removeAll(mine);
            return occupied;
        }

        /**
         * Сетка выходов панели — из кэша.
         */
        public Map<Integer, List<OutputOption>> buildOutputGrid(Long idPanel) {
            PanelDto panel = getPanelDtoById(idPanel);

            if (panel.getNameElement() == PANEL_OUTPUT_SWITCHING) {
                return OutputUtils.buildFixedGrid(List.of(OutputOption.values()));
            }

            List<OutputOption> selected = OutputUtils.extractSelectedOutputs(
                    panel.getListCondition(),
                    OutputUtils.payloadOffset(panel.getNameElement()));

            List<OutputOption> free = listCheckboxPanelId(panel.getObjectId(), idPanel);

            List<OutputOption> visible = new ArrayList<>(selected);
            for (OutputOption element : free) {
                if (!visible.contains(element)) visible.add(element);
            }
            return OutputUtils.buildFixedGrid(visible);
        }
    public Long deletePanel(Long panelId) {
        Long objectId = panelsService.deletePanel(panelId);
        panelsDtoCache.remove(objectId);
        logger.info("кэш сброшен после удаления панели {}", panelId);
        return objectId;
    }
    }

