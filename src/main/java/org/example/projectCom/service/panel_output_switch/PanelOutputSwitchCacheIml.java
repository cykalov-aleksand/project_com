package org.example.projectCom.service.panel_output_switch;

import lombok.RequiredArgsConstructor;
import org.example.projectCom.cache.CachePanelService;
import org.example.projectCom.dto.PanelDto;
import org.example.projectCom.model.Panels;
import org.example.projectCom.service.OutputUtils;
import org.example.projectCom.service.PanelsService;
import org.example.projectCom.service.errors.PanelValidationException;
import org.example.projectCom.setup_controller.OutputOption;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.example.projectCom.service.PanelsService.SWITCHING_PAYLOAD_SIZE;
import static org.example.projectCom.setup_controller.PanelNameOption.PANEL_OUTPUT_SWITCHING;

@Service
    @RequiredArgsConstructor
    public class PanelOutputSwitchCacheIml implements PanelOutputSwitch {
        private final PanelsService panelsService;
        private final CachePanelService cachePanelService;

        @Transactional
        public Panels save(Long panelId, Long objectId, List<String> outputs) {
            // панель из кэша: тип и принадлежность проверяем по DTO, без БД
            PanelDto panelDto = cachePanelService.getPanelDtoById(panelId);
            if (panelDto.getNameElement() != PANEL_OUTPUT_SWITCHING) {
                throw new IllegalArgumentException("Панель " + panelId + " не является панелью коммутации");
            }
            if (!panelDto.getObjectId().equals(objectId)) {
                throw new IllegalArgumentException(
                        "Панель " + panelId + " не относится к объекту " + objectId);
            }

            Set<OutputOption> selected = new HashSet<>(OutputUtils.parseSelectedOutputs(outputs));

            // каналы, занятые детьми, коммутация держит всегда: добавляем их к выбору,
            // независимо от того, что прислала форма — инвариант домена, а не правка ввода
            Set<OutputOption> taken = cachePanelService.takenByOthers(panelId);
            selected.addAll(taken);

            if (selected.isEmpty()) {
                throw new PanelValidationException(
                        "Панель коммутации должна иметь хотя бы один выход");
            }

            // защита сохраняется: если снятое всё же пересекается с занятым,
            // значит, пришёл некорректный запрос — но через обычный флоу это недостижимо
            Set<OutputOption> removed = new HashSet<>(
                    OutputUtils.extractSelectedOutputs(panelDto.getListCondition()));
            removed.removeAll(selected);

            if (!removed.isEmpty()) {
                Set<OutputOption> blocked = new HashSet<>(cachePanelService.takenByOthers(panelId));
                blocked.retainAll(removed);
                if (!blocked.isEmpty()) {
                    throw new PanelValidationException(
                            "Нельзя освободить каналы, занятые другими панелями: " +
                                    blocked.stream().map(OutputOption::getDisplayName)
                                            .collect(Collectors.joining(", ")));
                }
            }

            // живая сущность для записи — из БД, это правильный путь
            Panels panel = panelsService.getPanelById(panelId);

            // запись через кэш-слой: внутри persist вызовется reload
            return cachePanelService.persist(panel,
                    OutputUtils.buildPayload(panelDto.getNameElement(),
                            OutputUtils.packOutputs(new ArrayList<>(selected), SWITCHING_PAYLOAD_SIZE)));
        }

        @Override
        @Transactional
        public Long delete(Long panelId) {
            // проверка занятости — из кэша, без БД
            Set<OutputOption> taken = cachePanelService.takenByOthers(panelId);
            if (!taken.isEmpty()) {
                throw new PanelValidationException(
                        "Нельзя удалить панель коммутации: её каналы заняты другими панелями: " +
                                taken.stream().map(OutputOption::getDisplayName)
                                        .collect(Collectors.joining(", ")));
            }

            // удаление через кэш-слой: внутри — reload
            return cachePanelService.deletePanel(panelId);
        }
    }

