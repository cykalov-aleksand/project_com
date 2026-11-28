package org.example.projectCom.service.panelService.service;

import org.example.projectCom.dto.enum_panels.OutputOption;
import org.example.projectCom.dto.enum_panels.PanelNameOption;
import org.example.projectCom.model.EquipmentObject;
import org.example.projectCom.model.Panels;
import org.example.projectCom.repository.PanelsRepository;
import org.example.projectCom.service.OutputUtils;
import org.example.projectCom.service.errors.PanelValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PanelSynchronisationImlTest {

    @Mock
    private PanelsService panelsService;

    @Mock
    private PanelsRepository panelsRepository;

    @InjectMocks
    private PanelSynchronisationIml panelSynchronisation;

    private static final Long PANEL_ID = 20L;
    private static final Long OBJECT_ID = 5L;

    private EquipmentObject object(Long id) {
        EquipmentObject o = new EquipmentObject();
        o.setId(id);
        return o;
    }

    private Panels panel(PanelNameOption type, Long objectId) {
        Panels p = new Panels();
        p.setId(PANEL_ID);
        p.setNameElement(type);
        p.setObject(object(objectId));
        return p;
    }

    private String toHex(byte[] payload) {
        StringBuilder sb = new StringBuilder();
        for (byte b : payload) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("не синхронизация — исключение, persist не вызывается")
        void rejectsNonSyncPanel() {
            when(panelsService.getPanelById(PANEL_ID))
                    .thenReturn(panel(PanelNameOption.PANEL_OUTPUT_SWITCHING, OBJECT_ID));

            assertThatThrownBy(() ->
                    panelSynchronisation.save(PANEL_ID, 100, List.of("OUTPUT_1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("не является панелью синхронизации");

            verify(panelsService, never()).persist(any(), any());
        }

        @Test
        @DisplayName("недопустимый делитель — исключение до обращения к свободным каналам")
        void rejectsInvalidDivisor() {
            when(panelsService.getPanelById(PANEL_ID))
                    .thenReturn(panel(PanelNameOption.PANEL_SYNCHRONIZATION, OBJECT_ID));

            assertThatThrownBy(() ->
                    panelSynchronisation.save(PANEL_ID, 5001, List.of("OUTPUT_1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Недопустимый период");

            verify(panelsService, never()).listCheckboxPanelId(any(), any());
            verify(panelsService, never()).persist(any(), any());
        }
        @Test
        @DisplayName("занятые выходы — assertAllowed бросает, persist не вызывается")
        void rejectsOccupiedOutputs() {
            when(panelsService.getPanelById(PANEL_ID))
                    .thenReturn(panel(PanelNameOption.PANEL_SYNCHRONIZATION, OBJECT_ID));
            when(panelsService.listCheckboxPanelId(OBJECT_ID, PANEL_ID))
                    .thenReturn(List.of(OutputOption.OUTPUT_1));
            doThrow(new IllegalArgumentException("Выбраны занятые другими панелями выходы"))
                    .when(panelsService)
                    .assertAllowed(any(), any(), any());

            assertThatThrownBy(() ->
                    panelSynchronisation.save(PANEL_ID, 100, List.of("OUTPUT_1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("занятые");

            verify(panelsService, never()).persist(any(), any());
        }

        @Test
        @DisplayName("валидный выбор — persist получает кадр, читаемый обратно теми же утилитами")
        void persistsPayloadReadableBack() {
            when(panelsService.getPanelById(PANEL_ID))
                    .thenReturn(panel(PanelNameOption.PANEL_SYNCHRONIZATION, OBJECT_ID));
            when(panelsService.listCheckboxPanelId(OBJECT_ID, PANEL_ID))
                    .thenReturn(List.of(OutputOption.OUTPUT_1, OutputOption.OUTPUT_2,
                            OutputOption.OUTPUT_3));
            when(panelsService.persist(any(), any())).thenAnswer(inv -> inv.getArgument(0));

            panelSynchronisation.save(PANEL_ID, 100, List.of("OUTPUT_1", "OUTPUT_3"));

            ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(panelsService).persist(any(), payloadCaptor.capture());

            String hex = toHex(payloadCaptor.getValue());
            // round-trip: делитель и маски читаются тем же кодом, что читает сохранённый конфиг
            assertThat(OutputUtils.extractDivisor(hex)).isEqualTo(100);
            assertThat(OutputUtils.extractSelectedOutputs(
                    hex, OutputUtils.payloadOffset(PanelNameOption.PANEL_SYNCHRONIZATION)))
                    .containsExactlyInAnyOrder(OutputOption.OUTPUT_1, OutputOption.OUTPUT_3);
        }

        @Test
        @DisplayName("пустой выбор легален — синхронизатор сохраняется без масок")
        void allowsEmptySelection() {
            when(panelsService.getPanelById(PANEL_ID))
                    .thenReturn(panel(PanelNameOption.PANEL_SYNCHRONIZATION, OBJECT_ID));
            when(panelsService.listCheckboxPanelId(OBJECT_ID, PANEL_ID))
                    .thenReturn(List.of(OutputOption.OUTPUT_1));
            when(panelsService.persist(any(), any())).thenAnswer(inv -> inv.getArgument(0));

            panelSynchronisation.save(PANEL_ID, 100, List.of());

            verify(panelsService).persist(any(), any());
        }
    }
    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("на объекте есть генераторы — удаление запрещено, имена перечислены")
        void refusesWhenGeneratorsExist() {
            when(panelsService.getPanelById(PANEL_ID))
                    .thenReturn(panel(PanelNameOption.PANEL_SYNCHRONIZATION, OBJECT_ID));
            Panels generator1 = panel(PanelNameOption.PANEL_GENERATION_1, OBJECT_ID);
            Panels generator2 = panel(PanelNameOption.PANEL_GENERATION_2, OBJECT_ID);
            when(panelsRepository.findPanels(OBJECT_ID))
                    .thenReturn(List.of(generator1, generator2));

            assertThatThrownBy(() -> panelSynchronisation.delete(PANEL_ID))
                    .isInstanceOf(PanelValidationException.class)
                    .hasMessageContaining("Сначала удалите их");

            verify(panelsRepository, never()).delete(any());
        }

        @Test
        @DisplayName("генераторов нет — панель удаляется, возвращается objectId")
        void deletesWhenNoGenerators() {
            Panels sync = panel(PanelNameOption.PANEL_SYNCHRONIZATION, OBJECT_ID);
            when(panelsService.getPanelById(PANEL_ID)).thenReturn(sync);
            when(panelsRepository.findPanels(OBJECT_ID)).thenReturn(List.of());

            Long objectId = panelSynchronisation.delete(PANEL_ID);

            assertThat(objectId).isEqualTo(OBJECT_ID);
            verify(panelsRepository).delete(sync);
        }

        @Test
        @DisplayName("негенераторные панели на объекте удалению не мешают")
        void ignoresNonGeneratorSiblings() {
            Panels sync = panel(PanelNameOption.PANEL_SYNCHRONIZATION, OBJECT_ID);
            when(panelsService.getPanelById(PANEL_ID)).thenReturn(sync);
            // на объекте остаётся вторая панель синхронизации — она не блокирует
            when(panelsRepository.findPanels(OBJECT_ID))
                    .thenReturn(List.of(panel(PanelNameOption.PANEL_SYNCHRONIZATION, OBJECT_ID)));

            panelSynchronisation.delete(PANEL_ID);

            verify(panelsRepository).delete(sync);
        }

        @Test
        @DisplayName("панель не найдена — исключение, delete не вызывается")
        void throwsWhenPanelMissing() {
            when(panelsService.getPanelById(99L))
                    .thenThrow(new RuntimeException("Панель не найдена"));

            assertThatThrownBy(() -> panelSynchronisation.delete(99L))
                    .isInstanceOf(RuntimeException.class);

            verify(panelsRepository, never()).delete(any());
        }
    }
}
