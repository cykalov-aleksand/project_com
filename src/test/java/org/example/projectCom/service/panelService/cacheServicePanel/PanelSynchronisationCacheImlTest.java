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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PanelSynchronisationCacheImlTest {

    @Mock
    private PanelsService panelsService;

    @Mock
    private PanelServiceCache cachePanelService;

    @InjectMocks
    private PanelSynchronisationCacheIml panelSynchronisation;

    private static final Long PANEL_ID = 20L;
    private static final Long OBJECT_ID = 5L;

    // ---------- помощники ----------

    private PanelDto dto(Long id, Long objectId, PanelNameOption type) {
        PanelDto dto = new PanelDto();
        dto.setId(id);
        dto.setObjectId(objectId);
        dto.setNameElement(type);
        return dto;
    }

    private Panels livePanel() {
        Panels p = new Panels();
        p.setId(PANEL_ID);
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
        @DisplayName("не синхронизация — исключение, БД не затрагивается вовсе")
        void rejectsNonSyncPanel() {
            when(cachePanelService.getPanelDtoById(PANEL_ID))
                    .thenReturn(dto(PANEL_ID, OBJECT_ID, PanelNameOption.PANEL_OUTPUT_SWITCHING));

            assertThatThrownBy(() ->
                    panelSynchronisation.save(PANEL_ID, 100, List.of("OUTPUT_1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("не является панелью синхронизации");

            verify(panelsService, never()).getPanelById(any());
            verify(panelsService, never()).persist(any(), any());
            verify(cachePanelService, never()).persist(any(), any(), any());
        }

        @Test
        @DisplayName("недопустимый делитель — исключение до обращения к свободным каналам")
        void rejectsInvalidDivisor() {
            when(cachePanelService.getPanelDtoById(PANEL_ID))
                    .thenReturn(dto(PANEL_ID, OBJECT_ID, PanelNameOption.PANEL_SYNCHRONIZATION));

            assertThatThrownBy(() ->
                    panelSynchronisation.save(PANEL_ID, 5001, List.of("OUTPUT_1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Недопустимый период");

            verify(cachePanelService, never()).listCheckboxPanelId(any(), any());
            verify(panelsService, never()).getPanelById(any());
        }

        @Test
        @DisplayName("занятые выходы — assertAllowed бросает, живая сущность не запрашивается")
        void rejectsOccupiedOutputs() {
            when(cachePanelService.getPanelDtoById(PANEL_ID))
                    .thenReturn(dto(PANEL_ID, OBJECT_ID, PanelNameOption.PANEL_SYNCHRONIZATION));
            when(cachePanelService.listCheckboxPanelId(OBJECT_ID, PANEL_ID))
                    .thenReturn(List.of(OutputOption.OUTPUT_1));
            doThrow(new IllegalArgumentException("Выбраны занятые другими панелями выходы"))
                    .when(panelsService)
                    .assertAllowed(any(), any(), any());

            assertThatThrownBy(() ->
                    panelSynchronisation.save(PANEL_ID, 100, List.of("OUTPUT_1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("занятые");

            // сущность из БД запрашивается только после всех проверок — тут она не понадобилась
            verify(panelsService, never()).getPanelById(any());
            verify(cachePanelService, never()).persist(any(), any(), any());
        }

        @Test
        @DisplayName("валидный выбор — persist вызван с живой сущностью, кадром и objectId из кэша")
        void persistsThroughCacheWithCorrectArguments() {
            when(cachePanelService.getPanelDtoById(PANEL_ID))
                    .thenReturn(dto(PANEL_ID, OBJECT_ID, PanelNameOption.PANEL_SYNCHRONIZATION));
            when(cachePanelService.listCheckboxPanelId(OBJECT_ID, PANEL_ID))
                    .thenReturn(List.of(OutputOption.OUTPUT_1, OutputOption.OUTPUT_2,
                            OutputOption.OUTPUT_3));
            Panels live = livePanel();
            when(panelsService.getPanelById(PANEL_ID)).thenReturn(live);
            when(cachePanelService.persist(any(), any(), any())).thenReturn(live);

            panelSynchronisation.save(PANEL_ID, 100, List.of("OUTPUT_1", "OUTPUT_3"));

            ArgumentCaptor<Panels> panelCaptor = ArgumentCaptor.forClass(Panels.class);
            ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
            ArgumentCaptor<Long> objectIdCaptor = ArgumentCaptor.forClass(Long.class);
            verify(cachePanelService).persist(panelCaptor.capture(), payloadCaptor.capture(),
                    objectIdCaptor.capture());

            assertThat(panelCaptor.getValue()).isSameAs(live);
            assertThat(objectIdCaptor.getValue()).isEqualTo(OBJECT_ID);

            // round-trip: кадр читается теми же утилитами, что и сохранённый конфиг
            String hex = toHex(payloadCaptor.getValue());
            assertThat(OutputUtils.extractDivisor(hex)).isEqualTo(100);
            assertThat(OutputUtils.extractSelectedOutputs(
                    hex, OutputUtils.payloadOffset(PanelNameOption.PANEL_SYNCHRONIZATION)))
                    .containsExactlyInAnyOrder(OutputOption.OUTPUT_1, OutputOption.OUTPUT_3);
        }

        @Test
        @DisplayName("пустой выбор легален — persist вызывается")
        void allowsEmptySelection() {
            when(cachePanelService.getPanelDtoById(PANEL_ID))
                    .thenReturn(dto(PANEL_ID, OBJECT_ID, PanelNameOption.PANEL_SYNCHRONIZATION));
            when(cachePanelService.listCheckboxPanelId(OBJECT_ID, PANEL_ID))
                    .thenReturn(List.of(OutputOption.OUTPUT_1));
            when(panelsService.getPanelById(PANEL_ID)).thenReturn(livePanel());
            when(cachePanelService.persist(any(), any(), any())).thenReturn(livePanel());

            panelSynchronisation.save(PANEL_ID, 100, List.of());

            verify(cachePanelService).persist(any(), any(), any());
        }
    }
    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("на объекте есть генераторы — удаление запрещено, deletePanel не вызывается")
        void refusesWhenGeneratorsExist() {
            when(cachePanelService.getPanelDtoById(PANEL_ID))
                    .thenReturn(dto(PANEL_ID, OBJECT_ID, PanelNameOption.PANEL_SYNCHRONIZATION));
            when(cachePanelService.findPanelsIdObject(OBJECT_ID))
                    .thenReturn(List.of(
                            dto(30L, OBJECT_ID, PanelNameOption.PANEL_GENERATION_1),
                            dto(31L, OBJECT_ID, PanelNameOption.PANEL_GENERATION_2)));

            assertThatThrownBy(() -> panelSynchronisation.delete(PANEL_ID))
                    .isInstanceOf(PanelValidationException.class)
                    .hasMessageContaining("Сначала удалите их");

            verify(cachePanelService, never()).deletePanel(any());
        }

        @Test
        @DisplayName("генераторов нет — удаление уходит в кэш-сервис, его результат возвращается")
        void deletesThroughCacheService() {
            when(cachePanelService.getPanelDtoById(PANEL_ID))
                    .thenReturn(dto(PANEL_ID, OBJECT_ID, PanelNameOption.PANEL_SYNCHRONIZATION));
            when(cachePanelService.findPanelsIdObject(OBJECT_ID)).thenReturn(List.of());
            when(cachePanelService.deletePanel(PANEL_ID)).thenReturn(OBJECT_ID);

            Long objectId = panelSynchronisation.delete(PANEL_ID);

            assertThat(objectId).isEqualTo(OBJECT_ID);
            verify(cachePanelService).deletePanel(PANEL_ID);
        }

        @Test
        @DisplayName("сестринская панель синхронизации не блокирует удаление")
        void ignoresNonGeneratorSiblings() {
            when(cachePanelService.getPanelDtoById(PANEL_ID))
                    .thenReturn(dto(PANEL_ID, OBJECT_ID, PanelNameOption.PANEL_SYNCHRONIZATION));
            when(cachePanelService.findPanelsIdObject(OBJECT_ID))
                    .thenReturn(List.of(dto(21L, OBJECT_ID, PanelNameOption.PANEL_SYNCHRONIZATION)));
            when(cachePanelService.deletePanel(PANEL_ID)).thenReturn(OBJECT_ID);

            panelSynchronisation.delete(PANEL_ID);

            verify(cachePanelService).deletePanel(PANEL_ID);
        }

        @Test
        @DisplayName("панель не найдена — исключение, deletePanel не вызывается")
        void throwsWhenPanelMissing() {
            when(cachePanelService.getPanelDtoById(99L))
                    .thenThrow(new RuntimeException("Панель не найдена"));

            assertThatThrownBy(() -> panelSynchronisation.delete(99L))
                    .isInstanceOf(RuntimeException.class);

            verify(cachePanelService, never()).deletePanel(any());
        }
    }
}
