package org.example.projectCom.service.panelService;

import org.example.projectCom.dto.PanelDto;
import org.example.projectCom.dto.enum_panels.OutputOption;
import org.example.projectCom.dto.enum_panels.PanelNameOption;
import org.example.projectCom.model.Panels;
import org.example.projectCom.service.OutputUtils;
import org.example.projectCom.service.panelService.PanelOccupancyLogic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.example.projectCom.dto.enum_panels.PanelNameOption.PANEL_OUTPUT_SWITCHING;
import static org.example.projectCom.dto.enum_panels.PanelNameOption.PANEL_SYNCHRONIZATION;

class PanelOccupancyLogicTest {

    private static List<OutputOption> byteN(int n) {
        return Stream.of(OutputOption.values())
                .filter(o -> o.getByteIndex() == n)
                .toList();
    }

    // Маска для панели коммутации: ключ + 5 байтов масок
    private String switchingHex(List<OutputOption> selected) {
        return OutputUtils.toJsonbHex(
                OutputUtils.buildPayload(PANEL_OUTPUT_SWITCHING,
                        OutputUtils.packOutputs(selected, 5)));
    }

    // Маска для синхронизации: ключ + делитель + 5 байтов масок
    private String syncHex(List<OutputOption> selected) {
        return OutputUtils.toJsonbHex(
                OutputUtils.buildSyncPayload(PANEL_SYNCHRONIZATION, 100, selected));
    }

    private Panels panel(Long id, PanelNameOption type, List<OutputOption> selected) {
        Panels p = new Panels();
        p.setId(id);
        p.setNameElement(type);
        p.setOutputsJsonString(
                type == PANEL_OUTPUT_SWITCHING ? switchingHex(selected) : syncHex(selected));
        return p;
    }

    @Test
    void calculateFromEntities_freeStartsFromSwitching_selection() {
        // Коммутация заняла первый байт, дочерняя панель — второй.
        // Свободно = первый байт минус занятое дочерними. Занято = второй байт.
        Panels switching = panel(1L, PANEL_OUTPUT_SWITCHING, byteN(1));
        Panels sync = panel(2L, PANEL_SYNCHRONIZATION, byteN(2));

        PanelOccupancyLogic.OccupancyResult result =
                PanelOccupancyLogic.calculateFromEntities(List.of(switching, sync), -1L);

        assertThat(result.free()).containsExactlyInAnyOrderElementsOf(byteN(1));
        assertThat(result.occupied()).containsExactlyInAnyOrderElementsOf(byteN(2));
    }

    @Test
    void calculateFromEntities_childCanOvertakeSwitchingOutputs() {
        // Дочерняя панель выбрала те же выходы, что и коммутация — они больше не свободны
        Panels switching = panel(1L, PANEL_OUTPUT_SWITCHING, byteN(1));
        Panels sync = panel(2L, PANEL_SYNCHRONIZATION, byteN(1));

        PanelOccupancyLogic.OccupancyResult result =
                PanelOccupancyLogic.calculateFromEntities(List.of(switching, sync), -1L);

        assertThat(result.free()).isEmpty();
        assertThat(result.occupied()).containsExactlyInAnyOrderElementsOf(byteN(1));
    }

    @Test
    void calculateFromEntities_excludedPanelDoesNotOccupy() {
        Panels switching = panel(1L, PANEL_OUTPUT_SWITCHING, byteN(1));
        Panels sync = panel(2L, PANEL_SYNCHRONIZATION, byteN(2));

        PanelOccupancyLogic.OccupancyResult result =
                PanelOccupancyLogic.calculateFromEntities(List.of(switching, sync), 2L);

        // Панель 2 исключена — её выходы не в occupied и не вычтены из free
        assertThat(result.occupied()).isEmpty();
        assertThat(result.free()).containsExactlyInAnyOrderElementsOf(byteN(1));
    }

    @Test
    void calculateFromEntities_switchingPanelNeverOccupies() {
        // Коммутация без дочерних: ничего не занято, free — только её выбор
        Panels switching = panel(1L, PANEL_OUTPUT_SWITCHING, byteN(1));

        PanelOccupancyLogic.OccupancyResult result =
                PanelOccupancyLogic.calculateFromEntities(List.of(switching), -1L);

        assertThat(result.occupied()).isEmpty();
        assertThat(result.free()).containsExactlyInAnyOrderElementsOf(byteN(1));
    }

    @Test
    void calculateFromEntities_withoutSwitchingPanel_freeStartsEmpty() {
        Panels sync = panel(2L, PANEL_SYNCHRONIZATION, byteN(2));

        PanelOccupancyLogic.OccupancyResult result =
                PanelOccupancyLogic.calculateFromEntities(List.of(sync), -1L);

        assertThat(result.free()).isEmpty();
        assertThat(result.occupied()).containsExactlyInAnyOrderElementsOf(byteN(2));
    }

    @Test
    void calculateFromDtos_matchesEntitiesLogic() {
        // DTO-вариант: listCondition хранит ту же hex-строку
        PanelDto switching = new PanelDto();
        switching.setId(1L);
        switching.setNameElement(PANEL_OUTPUT_SWITCHING);
        switching.setListCondition(switchingHex(byteN(1)));

        PanelDto sync = new PanelDto();
        sync.setId(2L);
        sync.setNameElement(PANEL_SYNCHRONIZATION);
        sync.setListCondition(syncHex(byteN(2)));

        PanelOccupancyLogic.OccupancyResult result =
                PanelOccupancyLogic.calculateFromDtos(List.of(switching, sync), -1L);

        assertThat(result.free()).containsExactlyInAnyOrderElementsOf(byteN(1));
        assertThat(result.occupied()).containsExactlyInAnyOrderElementsOf(byteN(2));
    }
}
