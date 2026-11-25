package org.example.projectCom.service.panelService.service;

import org.example.projectCom.dto.enum_panels.OutputOption;
import org.example.projectCom.dto.enum_panels.PanelNameOption;
import org.example.projectCom.model.EquipmentObject;
import org.example.projectCom.model.Panels;
import org.example.projectCom.repository.PanelsRepository;
import org.example.projectCom.service.errors.NoFreeOutputsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PanelGenerationImlTest {

    @Mock
    private PanelsService panelsService;

    @Mock
    private PanelsRepository panelsRepository;

    @InjectMocks
    private PanelGenerationIml panelGeneration;

    // ---------- помощники ----------

     private EquipmentObject object(Long objectId) {
        EquipmentObject o = new EquipmentObject();
        o.setId(objectId);
        return o;
    }

    private Panels panel(Long id, PanelNameOption type, Long objectId) {
        Panels panel = new Panels();
        panel.setId(id);
        panel.setNameElement(type);
        panel.setObject(object(objectId));
        return panel;
    }

    private void stubPanelFound(Panels panel) {
        when(panelsService.getPanelById(panel.getId())).thenReturn(panel);
    }
    @Nested
    @DisplayName("save")
    class Save {

        private static final Long PANEL_ID = 10L;
        private static final Long OBJECT_ID = 5L;

        @Test
        @DisplayName("не генератор — исключение, persist не вызывается")
        void rejectsNonGeneratorPanel() {
            Panels switching = panel(PANEL_ID, PanelNameOption.PANEL_OUTPUT_SWITCHING, OBJECT_ID);
            stubPanelFound(switching);

            assertThatThrownBy(() -> panelGeneration.save(PANEL_ID, List.of("OUTPUT_1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("не является панелью генератора");

            verify(panelsService, never()).persist(any(), any());
        }

        @Test
        @DisplayName("пустой выбор и нет свободных — NoFreeOutputsException")
        void throwsWhenNothingSelectedAndNothingFree() {
            Panels gen = panel(PANEL_ID, PanelNameOption.PANEL_GENERATION_1, OBJECT_ID);
            stubPanelFound(gen);
            when(panelsService.listCheckboxPanelId(OBJECT_ID, PANEL_ID)).thenReturn(List.of());

            assertThatThrownBy(() -> panelGeneration.save(PANEL_ID, List.of()))
                    .isInstanceOf(NoFreeOutputsException.class)
                    .hasMessageContaining("свободных выходов нет");

            verify(panelsService, never()).persist(any(), any());
            verify(panelsService, never()).assertAllowed(any(), any(), anyString());
        }

        @Test
        @DisplayName("занятые выходы — assertAllowed бросает, persist не вызывается")
        void rejectsOccupiedOutputs() {
            Panels gen = panel(PANEL_ID, PanelNameOption.PANEL_GENERATION_1, OBJECT_ID);
            stubPanelFound(gen);
            when(panelsService.listCheckboxPanelId(OBJECT_ID, PANEL_ID))
                    .thenReturn(List.of(OutputOption.OUTPUT_1, OutputOption.OUTPUT_2));
            doThrow(new IllegalArgumentException("Выбраны занятые другими панелями выходы"))
                    .when(panelsService)
                    .assertAllowed(any(), any(), any());

            assertThatThrownBy(() ->
                    panelGeneration.save(PANEL_ID, List.of("OUTPUT_1", "OUTPUT_2")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("занятые");

            verify(panelsService, never()).persist(any(), any());
        }

        @Test
        @DisplayName("валидный выбор — persist вызван с payload, где биты выставлены верно")
        void persistsPayloadWithCorrectBits() {
            Panels gen = panel(PANEL_ID, PanelNameOption.PANEL_GENERATION_1, OBJECT_ID);
            stubPanelFound(gen);
            when(panelsService.listCheckboxPanelId(OBJECT_ID, PANEL_ID))
                    .thenReturn(List.of(OutputOption.OUTPUT_1, OutputOption.OUTPUT_2));
            when(panelsService.persist(any(), any())).thenReturn(gen);

            panelGeneration.save(PANEL_ID, List.of("OUTPUT_1", "OUTPUT_2"));

            ArgumentCaptor<Panels> panelCaptor = ArgumentCaptor.forClass(Panels.class);
            ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(panelsService).persist(panelCaptor.capture(), payloadCaptor.capture());

            assertThat(panelCaptor.getValue()).isSameAs(gen);

            byte[] payload = payloadCaptor.getValue();
            // GENERATOR: key(1) + outputs(5) = 6 байт
            assertThat(payload).hasSize(6);
            assertThat(payload[0] & 0xFF).isEqualTo(PanelNameOption.PANEL_GENERATION_1.getKeyCode());
            // OUTPUT_1 → байт 1, бит 0x01; OUTPUT_2 → байт 1, бит 0x02
            assertThat(payload[1] & 0xFF).isEqualTo(0x03);
            for (int i = 2; i < payload.length; i++) {
                assertThat(payload[i] & 0xFF).isZero();
            }
        }

        @Test
        @DisplayName("пустой выбор, но есть свободные — сохранение проходит (панель продолжает работать)")
        void persistsWhenNothingSelectedButFreeOutputsExist() {
            Panels gen = panel(PANEL_ID, PanelNameOption.PANEL_GENERATION_1, OBJECT_ID);
            stubPanelFound(gen);
            when(panelsService.listCheckboxPanelId(OBJECT_ID, PANEL_ID))
                    .thenReturn(List.of(OutputOption.OUTPUT_3));
            when(panelsService.persist(any(), any())).thenReturn(gen);

            panelGeneration.save(PANEL_ID, List.of());

            verify(panelsService).persist(any(), any());
        }
    }
    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("возвращает objectId и удаляет панель")
        void deletesAndReturnsObjectId() {
            Panels gen = panel(10L, PanelNameOption.PANEL_GENERATION_1, 5L);
            when(panelsService.getPanelById(10L)).thenReturn(gen);

            Long objectId = panelGeneration.delete(10L);

            assertThat(objectId).isEqualTo(5L);
            verify(panelsRepository).delete(gen);
        }

        @Test
        @DisplayName("панель не найдена — исключение, delete не вызывается")
        void throwsWhenPanelMissing() {
            when(panelsService.getPanelById(99L))
                    .thenThrow(new RuntimeException("Панель не найдена"));

            assertThatThrownBy(() -> panelGeneration.delete(99L))
                    .isInstanceOf(RuntimeException.class);

            verify(panelsRepository, never()).delete(any());
        }
    }}
