package org.example.projectCom.service.panelService;

import org.example.projectCom.dto.PanelDto;
import org.example.projectCom.dto.enum_panels.OutputOption;
import org.example.projectCom.dto.enum_panels.PanelNameOption;
import org.example.projectCom.model.Panels;
import org.example.projectCom.service.OutputUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PanelOccupancyLogic {
    /**
     * Считает занятые и свободные каналы по сущностям (для БД / PanelsService).
     */
    public static OccupancyResult calculateFromEntities(List<Panels> panels, Long excludePanelId) {
        List<OutputOption> free = new ArrayList<>();
        Set<OutputOption> occupied = new HashSet<>();

        // Сначала считаем все выходы панели коммутации как «базовые свободные»
        panels.stream()
                .filter(p -> p.getNameElement().equals(PanelNameOption.PANEL_OUTPUT_SWITCHING))
                .findFirst().ifPresent(switchingPanel -> free.addAll(OutputUtils.extractSelectedOutputs(
                        switchingPanel.getOutputsJsonString(),
                        OutputUtils.payloadOffset(PanelNameOption.PANEL_OUTPUT_SWITCHING))));

        for (Panels p : panels) {
            if (p.getId().equals(excludePanelId)) continue;
            if (p.getNameElement().equals(PanelNameOption.PANEL_OUTPUT_SWITCHING))
                continue; // коммутация не занимает чужие

            List<OutputOption> taken = OutputUtils.extractSelectedOutputs(
                    p.getOutputsJsonString(),
                    OutputUtils.payloadOffset(p.getNameElement()));

            occupied.addAll(taken);
            free.removeAll(taken); // вычитаем занятое из свободных
        }

        return new OccupancyResult(new ArrayList<>(free), occupied);
    }

    /**
     * Считает занятые и свободные каналы по DTO (для кэша / CachePanelService).
     */
    public static OccupancyResult calculateFromDtos(List<PanelDto> panels, Long excludePanelId) {
        List<OutputOption> free = new ArrayList<>();
        Set<OutputOption> occupied = new HashSet<>();

        panels.stream()
                .filter(p -> p.getNameElement().equals(PanelNameOption.PANEL_OUTPUT_SWITCHING))
                .findFirst().ifPresent(switchingPanel -> free.addAll(OutputUtils.extractSelectedOutputs(
                        switchingPanel.getListCondition(),
                        OutputUtils.payloadOffset(PanelNameOption.PANEL_OUTPUT_SWITCHING))));

        for (PanelDto p : panels) {
            if (p.getId().equals(excludePanelId)) continue;
            if (p.getNameElement().equals(PanelNameOption.PANEL_OUTPUT_SWITCHING)) continue;

            List<OutputOption> taken = OutputUtils.extractSelectedOutputs(
                    p.getListCondition(),
                    OutputUtils.payloadOffset(p.getNameElement()));

            occupied.addAll(taken);
            free.removeAll(taken);
        }

        return new OccupancyResult(new ArrayList<>(free), occupied);
    }

    /**
     * Простой контейнер для результата.
     */
    public record OccupancyResult(List<OutputOption> free, Set<OutputOption> occupied) {
    }
}

