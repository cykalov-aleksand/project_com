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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PanelOutputSwitchImlTest {

    @Mock
    private PanelsService panelsService;

    @Mock
    private PanelsRepository panelsRepository;

    @InjectMocks
    private PanelOutputSwitchIml panelOutputSwitch;

    private static final Long PANEL_ID = 10L;
    private static final Long OBJECT_ID = 5L;
    private static final int SWITCHING_PAYLOAD_SIZE = 5; // подгони под свою константу

    private EquipmentObject object(Long id) {
        EquipmentObject o = new EquipmentObject();
        o.setId(id);
        return o;
    }

    private Panels panel(PanelNameOption type, Long objectId, String configHex) {
        Panels p = new Panels();
        p.setId(PANEL_ID);
        p.setNameElement(type);
        p.setObject(object(objectId));
        p.setOutputsJsonString(configHex);
        return p;
    }

    /** Собирает сохранённый кадр (ключ + маски) и возвращает его hex-представление. */
    private String configHex(OutputOption... options) {
        byte[] payload = OutputUtils.buildPayload(
                PanelNameOption.PANEL_OUTPUT_SWITCHING,
                OutputUtils.packOutputs(new ArrayList<>(List.of(options)),
                        SWITCHING_PAYLOAD_SIZE));
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
        @DisplayName("не коммутация — исключение, persist не вызывается")
        void rejectsNonSwitchingPanel() {
            when(panelsService.getPanelById(PANEL_ID))
                    .thenReturn(panel(PanelNameOption.PANEL_SYNCHRONIZATION, OBJECT_ID, null));

            assertThatThrownBy(() ->
                    panelOutputSwitch.save(PANEL_ID, OBJECT_ID, List.of("OUTPUT_1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("не является панелью коммутации");

            verify(panelsService, never()).persist(any(), any());
        }

        @Test
        @DisplayName("чужой объект — исключение, persist не вызывается")
        void rejectsWrongObject() {
            when(panelsService.getPanelById(PANEL_ID))
                    .thenReturn(panel(PanelNameOption.PANEL_OUTPUT_SWITCHING, OBJECT_ID, null));

            assertThatThrownBy(() ->
                    panelOutputSwitch.save(PANEL_ID, 999L, List.of("OUTPUT_1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("не относится к объекту");

            verify(panelsService, never()).persist(any(), any());
        }

        @Test
        @DisplayName("занятое добавляется к выбору: persist получает объединение битов")
        void mergesTakenIntoSelection() {
            when(panelsService.getPanelById(PANEL_ID))
                    .thenReturn(panel(PanelNameOption.PANEL_OUTPUT_SWITCHING,
                            OBJECT_ID, configHex(OutputOption.OUTPUT_3)));
            when(panelsService.takenByOthers(PANEL_ID)).thenReturn(Set.of(OutputOption.OUTPUT_1));
            when(panelsService.persist(any(), any())).thenAnswer(inv -> inv.getArgument(0));

            panelOutputSwitch.save(PANEL_ID, OBJECT_ID, List.of("OUTPUT_2"));

            ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(panelsService).persist(any(), payloadCaptor.capture());

            byte[] payload = payloadCaptor.getValue();
            assertThat(payload[0] & 0xFF)
                    .isEqualTo(PanelNameOption.PANEL_OUTPUT_SWITCHING.getKeyCode());
            // OUTPUT_2 (форма) | OUTPUT_1 (занято) | OUTPUT_3 (старый конфиг, но он тоже в выборе)
            assertThat(payload[1] & 0xFF).isEqualTo(0x03);
        }
        @Test
        @DisplayName("пустой выбор без занятых — PanelValidationException")
        void throwsWhenNothingSelectedAndNothingTaken() {
            when(panelsService.getPanelById(PANEL_ID))
                    .thenReturn(panel(PanelNameOption.PANEL_OUTPUT_SWITCHING,
                            OBJECT_ID, configHex(OutputOption.OUTPUT_1)));
            when(panelsService.takenByOthers(PANEL_ID)).thenReturn(Set.of());

            assertThatThrownBy(() -> panelOutputSwitch.save(PANEL_ID, OBJECT_ID, null))
                    .isInstanceOf(PanelValidationException.class)
                    .hasMessageContaining("хотя бы один выход");

            verify(panelsService, never()).persist(any(), any());
        }

        @Test
        @DisplayName("пустой выбор, но есть занятое — сохранение проходит (инвариант домена)")
        void passesWhenEmptyButTakenExists() {
            when(panelsService.getPanelById(PANEL_ID))
                    .thenReturn(panel(PanelNameOption.PANEL_OUTPUT_SWITCHING,
                            OBJECT_ID, configHex(OutputOption.OUTPUT_1)));
            when(panelsService.takenByOthers(PANEL_ID)).thenReturn(Set.of(OutputOption.OUTPUT_1));
            when(panelsService.persist(any(), any())).thenAnswer(inv -> inv.getArgument(0));

            panelOutputSwitch.save(PANEL_ID, OBJECT_ID, null);

            verify(panelsService).persist(any(), any());
        }

        @Test
        @DisplayName("попытка освободить чужое — PanelValidationException, persist не вызывается")
        void refusesToReleaseOccupiedChannel() {
            // несогласованный запрос: сначала takenByOthers вернул пусто,
            // потом (при проверке снятого) — занятый OUTPUT_1
            when(panelsService.getPanelById(PANEL_ID))
                    .thenReturn(panel(PanelNameOption.PANEL_OUTPUT_SWITCHING,
                            OBJECT_ID, configHex(OutputOption.OUTPUT_1, OutputOption.OUTPUT_2)));
            when(panelsService.takenByOthers(PANEL_ID))
                    .thenReturn(Set.of())                    // первый вызов: занятых нет
                    .thenReturn(Set.of(OutputOption.OUTPUT_1)); // второй: занято

            assertThatThrownBy(() ->
                    panelOutputSwitch.save(PANEL_ID, OBJECT_ID, List.of("OUTPUT_2")))
                    .isInstanceOf(PanelValidationException.class)
                    .hasMessageContaining("Нельзя освободить каналы");

            verify(panelsService, never()).persist(any(), any());
        }

        @Test
        @DisplayName("освобождение свободного канала — проходит, бит снимается в payload")
        void releasesFreeChannel() {
            when(panelsService.getPanelById(PANEL_ID))
                    .thenReturn(panel(PanelNameOption.PANEL_OUTPUT_SWITCHING,
                            OBJECT_ID, configHex(OutputOption.OUTPUT_1, OutputOption.OUTPUT_2)));
            when(panelsService.takenByOthers(PANEL_ID)).thenReturn(Set.of());
            when(panelsService.persist(any(), any())).thenAnswer(inv -> inv.getArgument(0));

            panelOutputSwitch.save(PANEL_ID, OBJECT_ID, List.of("OUTPUT_2"));

            ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(panelsService).persist(any(), payloadCaptor.capture());
            // остался только OUTPUT_2, OUTPUT_1 снят
            assertThat(payloadCaptor.getValue()[1] & 0xFF).isEqualTo(0x02);
        }
    }
    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("каналы заняты — PanelValidationException, панель не удаляется")
        void refusesWhenChannelsTaken() {
            when(panelsService.getPanelById(PANEL_ID))
                    .thenReturn(panel(PanelNameOption.PANEL_OUTPUT_SWITCHING, OBJECT_ID, null));
            when(panelsService.takenByOthers(PANEL_ID)).thenReturn(Set.of(OutputOption.OUTPUT_1));

            assertThatThrownBy(() -> panelOutputSwitch.delete(PANEL_ID))
                    .isInstanceOf(PanelValidationException.class)
                    .hasMessageContaining("Нельзя удалить панель коммутации");

            verify(panelsRepository, never()).delete(any());
        }

        @Test
        @DisplayName("каналы свободны — панель удаляется, возвращается objectId")
        void deletesAndReturnsObjectId() {
            Panels p = panel(PanelNameOption.PANEL_OUTPUT_SWITCHING, OBJECT_ID, null);
            when(panelsService.getPanelById(PANEL_ID)).thenReturn(p);
            when(panelsService.takenByOthers(PANEL_ID)).thenReturn(Set.of());

            Long objectId = panelOutputSwitch.delete(PANEL_ID);

            assertThat(objectId).isEqualTo(OBJECT_ID);
            verify(panelsRepository).delete(p);
        }

        @Test
        @DisplayName("панель не найдена — исключение, delete не вызывается")
        void throwsWhenPanelMissing() {
            when(panelsService.getPanelById(99L))
                    .thenThrow(new RuntimeException("Панель не найдена"));

            assertThatThrownBy(() -> panelOutputSwitch.delete(99L))
                    .isInstanceOf(RuntimeException.class);

            verify(panelsRepository, never()).delete(any());
        }
    }
}
