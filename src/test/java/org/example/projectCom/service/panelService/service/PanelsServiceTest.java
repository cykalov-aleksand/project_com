package org.example.projectCom.service.panelService.service;

import jakarta.persistence.EntityNotFoundException;
import org.example.projectCom.dto.enum_panels.OutputOption;
import org.example.projectCom.dto.enum_panels.PanelNameOption;
import org.example.projectCom.model.EquipmentObject;
import org.example.projectCom.model.Panels;
import org.example.projectCom.repository.ObjectRepository;
import org.example.projectCom.repository.PanelsRepository;
import org.example.projectCom.service.OutputUtils;
import org.example.projectCom.service.panelService.service.PanelsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.example.projectCom.dto.enum_panels.PanelNameOption.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class PanelsServiceTest {

    @Mock
    private PanelsRepository panelsRepository;
    @Mock
    private ObjectRepository objectRepository;

    @InjectMocks
    private PanelsService panelsService;

    private EquipmentObject equipmentObject;
    private Panels panel;

    @BeforeEach
    void setUp() {
        equipmentObject = new EquipmentObject();
        equipmentObject.setId(42L);

        panel = new Panels();
        panel.setId(1L);
        panel.setNameElement(PANEL_OUTPUT_SWITCHING);
        // object проставляем в тех тестах, где он нужен — по ситуации
    }

    // ---------- getPanelById ----------

    @Test
    void getPanelById_returnsPanel_whenExists() {
        when(panelsRepository.findById(1L)).thenReturn(Optional.of(panel));

        Panels result = panelsService.getPanelById(1L);

        assertThat(result).isSameAs(panel);
    }

    @Test
    void getPanelById_throws_whenNotFound() {
        when(panelsRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> panelsService.getPanelById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ---------- createPanel ----------

    @Test
    void createPanel_savesPanelWithObjectAndType() {
        when(panelsRepository.findPanels(42L)).thenReturn(List.of()); // дубликата нет
        when(objectRepository.findById(42L)).thenReturn(Optional.of(equipmentObject));
        when(panelsRepository.save(any(Panels.class)))
                .thenAnswer(inv -> inv.getArgument(0)); // save возвращает то, что ему передали

        Panels created = panelsService.createPanel(42L, PANEL_OUTPUT_SWITCHING);

        assertThat(created.getObject()).isEqualTo(equipmentObject);
        assertThat(created.getNameElement()).isEqualTo(PANEL_OUTPUT_SWITCHING);
        verify(panelsRepository).save(created);
    }

    @Test
    void createPanel_throws_whenPanelTypeAlreadyExists() {
        Panels existing = new Panels();
        existing.setNameElement(PANEL_OUTPUT_SWITCHING);
        when(panelsRepository.findPanels(42L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> panelsService.createPanel(42L, PANEL_OUTPUT_SWITCHING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("уже есть");

        verify(panelsRepository, never()).save(any()); // дубль не сохраняется
        verifyNoInteractions(objectRepository);         // до объекта дело не дошло
    }

    @Test
    void createPanel_throws_whenObjectNotFound() {
        when(panelsRepository.findPanels(42L)).thenReturn(List.of());
        when(objectRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> panelsService.createPanel(42L, PANEL_OUTPUT_SWITCHING))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("42");

        verify(panelsRepository, never()).save(any());
    }

    // ---------- existsPanelType ----------

    @Test
    void existsPanelType_true_whenTypePresent() {
        Panels existing = new Panels();
        existing.setNameElement(PANEL_SYNCHRONIZATION);
        when(panelsRepository.findPanels(42L)).thenReturn(List.of(existing));

        assertThat(panelsService.existsPanelType(42L, PANEL_SYNCHRONIZATION)).isTrue();
    }

    @Test
    void existsPanelType_false_whenTypeAbsent() {
        when(panelsRepository.findPanels(42L)).thenReturn(List.of());

        assertThat(panelsService.existsPanelType(42L, PANEL_SYNCHRONIZATION)).isFalse();
    }

    // ---------- getAvailablePanelTypes ----------

    @Test
    void getAvailablePanelTypes_onlySwitching_whenNoPanelsYet() {
        when(panelsRepository.findPanels(42L)).thenReturn(List.of());

        List<PanelNameOption> result = panelsService.getAvailablePanelTypes(42L);

        // Пока нет коммутации — доступна только она, даже если других панелей тоже нет
        assertThat(result).containsExactly(PANEL_OUTPUT_SWITCHING);
    }

    @Test
    void getAvailablePanelTypes_onlySync_whenSwitchingExists() {
        when(panelsRepository.findPanels(42L)).thenReturn(List.of(panel)); // коммутация есть

        List<PanelNameOption> result = panelsService.getAvailablePanelTypes(42L);

        assertThat(result).containsExactly(PANEL_SYNCHRONIZATION);
    }

    @Test
    void getAvailablePanelTypes_allExceptExisting_whenBothBasePanelsExist() {
        Panels sync = new Panels();
        sync.setNameElement(PANEL_SYNCHRONIZATION);
        when(panelsRepository.findPanels(42L)).thenReturn(List.of(panel, sync));

        List<PanelNameOption> result = panelsService.getAvailablePanelTypes(42L);

        assertThat(result)
                .doesNotContain(PANEL_OUTPUT_SWITCHING, PANEL_SYNCHRONIZATION)
                .hasSize(PanelNameOption.values().length - 2); // все остальные типы открыты
    }

    // ---------- getPanelJson ----------

    @Test
    void getPanelJson_returnsJsonString_ofMatchingPanel() {
        panel.setOutputsJsonString("01FF");
        when(panelsRepository.findPanels(42L)).thenReturn(List.of(panel));

        String json = panelsService.getPanelJson(42L, PANEL_OUTPUT_SWITCHING);

        assertThat(json).isEqualTo("01FF");
    }

    @Test
    void getPanelJson_returnsNull_whenPanelOfTypeMissing() {
        when(panelsRepository.findPanels(42L)).thenReturn(List.of());

        assertThat(panelsService.getPanelJson(42L, PANEL_OUTPUT_SWITCHING)).isNull();
    }

    // ---------- persist ----------

    @Test
    void persist_setsJsonAndSaves() {
        when(panelsRepository.save(any(Panels.class)))
                .thenAnswer(inv -> inv.getArgument(0)); // save возвращает то же, что получил

        Panels saved = panelsService.persist(panel, new byte[]{0x01, 0x02});

        assertThat(saved.getOutputsJsonString()).isEqualTo("\"0102\"");
        verify(panelsRepository).save(panel);
        verifyNoMoreInteractions(panelsRepository);
    }
    // ---------- assertAllowed ----------

    @Test
    void assertAllowed_doesNotThrow_whenAllSelectedAreAllowed() {
        OutputOption a = OutputOption.values()[0];

        panelsService.assertAllowed(List.of(a), Set.of(OutputOption.values()), "ошибка");
        // исключение не брошено — тест прошёл
    }

    @Test
    void assertAllowed_throws_whenSelectedNotInAllowed() {
        OutputOption a = OutputOption.values()[0];

        assertThatThrownBy(() -> panelsService.assertAllowed(List.of(a), Set.of(), "выход занят"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("выход занят");
    }

    // ---------- deletePanel ----------

    @Test
    void deletePanel_deletesAndReturnsObjectId() {
        panel.setObject(equipmentObject);
        when(panelsRepository.findById(1L)).thenReturn(Optional.of(panel));

        Long objectId = panelsService.deletePanel(1L);

        assertThat(objectId).isEqualTo(42L);
        verify(panelsRepository).delete(panel);
    }

    @Test
    void deletePanel_throws_whenPanelNotFound() {
        when(panelsRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> panelsService.deletePanel(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");

        verify(panelsRepository, never()).delete(any());
    }

    // ---------- ветки на реальных статических утилитах ----------

    @Test
    void buildOutputGrid_switchingPanel_containsAllOutputs() {
        panel.setNameElement(PANEL_OUTPUT_SWITCHING);
        when(panelsRepository.findById(1L)).thenReturn(Optional.of(panel));

        Map<Integer, List<OutputOption>> grid = panelsService.buildOutputGrid(1L);

        assertThat(grid.values().stream().flatMap(List::stream))
                .containsExactlyInAnyOrder(OutputOption.values());
    }

    @Test
    void groupOfPointersWeekends_switchingPanel_groupsAllByByteIndex() {
        Map<Integer, List<OutputOption>> groups =
                panelsService.groupOfPointersWeekends(PANEL_OUTPUT_SWITCHING, 42L, 1L);

        assertThat(groups.values().stream().flatMap(List::stream))
                .containsExactlyInAnyOrder(OutputOption.values());
    }
    // ---------- listCheckboxPanelId ----------

    @Test
    void listCheckboxPanelId_returnsFreeOutputs() {
        Panels switching = panelOccupancy(1L, PANEL_OUTPUT_SWITCHING, byteN(1));
        Panels sync = panelOccupancy(2L, PANEL_SYNCHRONIZATION, byteN(2));
        when(panelsRepository.findPanels(42L)).thenReturn(List.of(switching, sync));

        List<OutputOption> free = panelsService.listCheckboxPanelId(42L, -1L);

        assertThat(free).containsExactlyInAnyOrderElementsOf(byteN(1));
    }

// ---------- takenByOthers ----------

    @Test
    void takenByOthers_subtractsOwnOutputsForChildPanel() {
        Panels self = panelOccupancy(2L, PANEL_SYNCHRONIZATION, byteN(2));
        self.setObject(equipmentObject);
        when(panelsRepository.findById(2L)).thenReturn(Optional.of(self));
        when(panelsRepository.findPanels(42L)).thenReturn(List.of(
                panelOccupancy(1L, PANEL_OUTPUT_SWITCHING, byteN(1)),
                panelOccupancy(3L, PANEL_GENERATION_1, byteN(2)),   // другой ребёнок занял тот же байт
                self));

        Set<OutputOption> taken = panelsService.takenByOthers(2L);

        // Свои выходы (второй байт) вычтены — занятыми остались только чужие с тем же выбором
        assertThat(taken).isEmpty(); // PANEL_GENERATOR занял ровно то же, что и мы
    }

// ---------- buildOutputGrid (дочерняя панель) ----------

    @Test
    void buildOutputGrid_childPanel_showsSelectedPlusFree() {
        Panels self = panelOccupancy(2L, PANEL_SYNCHRONIZATION, byteN(2));
        self.setObject(equipmentObject);
        when(panelsRepository.findById(2L)).thenReturn(Optional.of(self));
        when(panelsRepository.findPanels(42L)).thenReturn(List.of(
                panelOccupancy(1L, PANEL_OUTPUT_SWITCHING, byteN(1)),
                self));

        Map<Integer, List<OutputOption>> grid = panelsService.buildOutputGrid(2L);

        // Видимы и выбранные (байт 2), и свободные (байт 1 от коммутации)
        assertThat(grid.values().stream().flatMap(List::stream))
                .containsExactlyInAnyOrderElementsOf(Stream.of(byteN(1), byteN(2)).flatMap(List::stream).toList());
    }
    // ---------- хелперы ----------

    private static List<OutputOption> byteN(int n) {
        return Arrays.stream(OutputOption.values())
                .filter(o -> o.getByteIndex() == n)
                .toList();
    }

    private Panels panelOccupancy(Long id, PanelNameOption type, List<OutputOption> selected) {
        Panels p = new Panels();
        p.setId(id);
        p.setNameElement(type);
        byte[] packed = OutputUtils.packOutputs(selected, 5);
        if (type == PANEL_SYNCHRONIZATION) {
            p.setOutputsJsonString(OutputUtils.toJsonbHex(
                    OutputUtils.buildSyncPayload(type, 100, selected)));
        } else {
            p.setOutputsJsonString(OutputUtils.toJsonbHex(
                    OutputUtils.buildPayload(PANEL_OUTPUT_SWITCHING, packed)));
        }
        return p;
    }
}