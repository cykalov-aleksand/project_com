package org.example.projectCom.service.panelService.cacheServicePanel;

import org.example.projectCom.dto.PanelDto;
import org.example.projectCom.dto.enum_panels.OutputOption;
import org.example.projectCom.dto.enum_panels.PanelNameOption;
import org.example.projectCom.model.Panels;
import org.example.projectCom.service.OutputUtils;
import org.example.projectCom.service.errors.PanelValidationException;
import org.example.projectCom.service.panelService.service.PanelsService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PanelOutputSwitchCacheImlTest {

    @Mock
    private PanelsService panelsService;

    @Mock
    private PanelServiceCache cachePanelService;

    @InjectMocks
    private PanelOutputSwitchCacheIml panelOutputSwitch;

    private static final Long PANEL_ID = 10L;
    private static final Long OBJECT_ID = 5L;
    private static final int SWITCHING_PAYLOAD_SIZE = 5; // подгони под свою константу

    // ---------- помощники ----------

    private PanelDto dto(PanelNameOption type, String listCondition) {
        PanelDto dto = new PanelDto();
        dto.setId(PANEL_ID);
        dto.setObjectId(OBJECT_ID);
        dto.setNameElement(type);
        dto.setListCondition(listCondition);
        return dto;
    }

    private Panels livePanel() {
        Panels p = new Panels();
        p.setId(PANEL_ID);
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
        @DisplayName("не коммутация — исключение, БД и свободные каналы не затрагиваются")
        void rejectsNonSwitchingPanel() {
            when(cachePanelService.getPanelDtoById(PANEL_ID))
                    .thenReturn(dto(PanelNameOption.PANEL_SYNCHRONIZATION, null));

            assertThatThrownBy(() ->
                    panelOutputSwitch.save(PANEL_ID, OBJECT_ID, List.of("OUTPUT_1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("не является панелью коммутации");

            verify(cachePanelService, never()).takenByOthers(any());
            verify(panelsService, never()).getPanelById(any());
            verify(cachePanelService, never()).persist(any(), any(), any());
        }

        @Test
        @DisplayName("чужой объект — исключение, БД не трогаем")
        void rejectsWrongObject() {
            when(cachePanelService.getPanelDtoById(PANEL_ID))
                    .thenReturn(dto(PanelNameOption.PANEL_OUTPUT_SWITCHING, null));

            assertThatThrownBy(() ->
                    panelOutputSwitch.save(PANEL_ID, 999L, List.of("OUTPUT_1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("не относится к объекту");

            verify(panelsService, never()).getPanelById(any());
            verify(cachePanelService, never()).persist(any(), any(), any());
        }

        @Test
        @DisplayName("занятое добавляется к выбору: persist получает объединение битов")
        void mergesTakenIntoSelection() {
            when(cachePanelService.getPanelDtoById(PANEL_ID))
                    .thenReturn(dto(PanelNameOption.PANEL_OUTPUT_SWITCHING,
                            configHex(OutputOption.OUTPUT_3)));
            when(cachePanelService.takenByOthers(PANEL_ID))
                    .thenReturn(Set.of(OutputOption.OUTPUT_1));
            Panels live = livePanel();
            when(panelsService.getPanelById(PANEL_ID)).thenReturn(live);
            when(cachePanelService.persist(any(), any(), any())).thenReturn(live);

            panelOutputSwitch.save(PANEL_ID, OBJECT_ID, List.of("OUTPUT_2"));

            ArgumentCaptor<Panels> panelCaptor = ArgumentCaptor.forClass(Panels.class);
            ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
            ArgumentCaptor<Long> objectIdCaptor = ArgumentCaptor.forClass(Long.class);
            verify(cachePanelService).persist(panelCaptor.capture(), payloadCaptor.capture(),
                    objectIdCaptor.capture());

            assertThat(panelCaptor.getValue()).isSameAs(live);
            assertThat(objectIdCaptor.getValue()).isEqualTo(OBJECT_ID);

            byte[] payload = payloadCaptor.getValue();
            assertThat(payload[0] & 0xFF)
                    .isEqualTo(PanelNameOption.PANEL_OUTPUT_SWITCHING.getKeyCode());
            // OUTPUT_2 (форма) | OUTPUT_1 (занято); OUTPUT_3 из старого конфига снят формой
            assertThat(payload[1] & 0xFF).isEqualTo(0x03);
        }

        @Test
        @DisplayName("пустой выбор без занятых — PanelValidationException, БД не трогаем")
        void throwsWhenNothingSelectedAndNothingTaken() {
            when(cachePanelService.getPanelDtoById(PANEL_ID))
                    .thenReturn(dto(PanelNameOption.PANEL_OUTPUT_SWITCHING,
                            configHex(OutputOption.OUTPUT_1)));
            when(cachePanelService.takenByOthers(PANEL_ID)).thenReturn(Set.of());

            assertThatThrownBy(() -> panelOutputSwitch.save(PANEL_ID, OBJECT_ID, null))
                    .isInstanceOf(PanelValidationException.class)
                    .hasMessageContaining("хотя бы один выход");

            verify(panelsService, never()).getPanelById(any());
            verify(cachePanelService, never()).persist(any(), any(), any());
        }

        @Test
        @DisplayName("пустой выбор, но есть занятое — сохранение проходит (инвариант домена)")
        void passesWhenEmptyButTakenExists() {
            when(cachePanelService.getPanelDtoById(PANEL_ID))
                    .thenReturn(dto(PanelNameOption.PANEL_OUTPUT_SWITCHING,
                            configHex(OutputOption.OUTPUT_1)));
            when(cachePanelService.takenByOthers(PANEL_ID))
                    .thenReturn(Set.of(OutputOption.OUTPUT_1));
            when(panelsService.getPanelById(PANEL_ID)).thenReturn(livePanel());
            when(cachePanelService.persist(any(), any(), any())).thenReturn(livePanel());

            panelOutputSwitch.save(PANEL_ID, OBJECT_ID, null);

            verify(cachePanelService).persist(any(), any(), any());
        }

        @Test
        @DisplayName("попытка освободить чужое — PanelValidationException, persist не вызывается")
        void refusesToReleaseOccupiedChannel() {
            // несогласованный запрос: takenByOthers дважды отвечает по-разному
            when(cachePanelService.getPanelDtoById(PANEL_ID))
                    .thenReturn(dto(PanelNameOption.PANEL_OUTPUT_SWITCHING,
                            configHex(OutputOption.OUTPUT_1, OutputOption.OUTPUT_2)));
            when(cachePanelService.takenByOthers(PANEL_ID))
                    .thenReturn(Set.of())                            // первый вызов: занятых нет
                    .thenReturn(Set.of(OutputOption.OUTPUT_1));      // второй: занято

            assertThatThrownBy(() ->
                    panelOutputSwitch.save(PANEL_ID, OBJECT_ID, List.of("OUTPUT_2")))
                    .isInstanceOf(PanelValidationException.class)
                    .hasMessageContaining("Нельзя освободить каналы");

            verify(panelsService, never()).getPanelById(any());
            verify(cachePanelService, never()).persist(any(), any(), any());
        }

        @Test
        @DisplayName("освобождение свободного канала — проходит, бит снимается в payload")
        void releasesFreeChannel() {
            when(cachePanelService.getPanelDtoById(PANEL_ID))
                    .thenReturn(dto(PanelNameOption.PANEL_OUTPUT_SWITCHING,
                            configHex(OutputOption.OUTPUT_1, OutputOption.OUTPUT_2)));
            when(cachePanelService.takenByOthers(PANEL_ID)).thenReturn(Set.of());
            when(panelsService.getPanelById(PANEL_ID)).thenReturn(livePanel());
            when(cachePanelService.persist(any(), any(), any())).thenReturn(livePanel());

            panelOutputSwitch.save(PANEL_ID, OBJECT_ID, List.of("OUTPUT_2"));

            ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(cachePanelService).persist(any(), payloadCaptor.capture(), eq(OBJECT_ID));
            // остался только OUTPUT_2, OUTPUT_1 снят
            assertThat(payloadCaptor.getValue()[1] & 0xFF).isEqualTo(0x02);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("каналы заняты — PanelValidationException, deletePanel не вызывается")
        void refusesWhenChannelsTaken() {
            when(cachePanelService.takenByOthers(PANEL_ID))
                    .thenReturn(Set.of(OutputOption.OUTPUT_1));

            assertThatThrownBy(() -> panelOutputSwitch.delete(PANEL_ID))
                    .isInstanceOf(PanelValidationException.class)
                    .hasMessageContaining("Нельзя удалить панель коммутации");

            verify(cachePanelService, never()).deletePanel(any());
        }

        @Test
        @DisplayName("каналы свободны — удаление через кэш-сервис, его результат возвращается")
        void deletesThroughCacheService() {
            when(cachePanelService.takenByOthers(PANEL_ID)).thenReturn(Set.of());
            when(cachePanelService.deletePanel(PANEL_ID)).thenReturn(OBJECT_ID);

            Long objectId = panelOutputSwitch.delete(PANEL_ID);

            assertThat(objectId).isEqualTo(OBJECT_ID);
            verify(cachePanelService).deletePanel(PANEL_ID);
        }
    }
}
