package org.example.projectCom.service.panelService.cacheServicePanel;

import org.example.projectCom.dto.PanelDto;
import org.example.projectCom.dto.enum_panels.OutputOption;
import org.example.projectCom.dto.enum_panels.PanelNameOption;
import org.example.projectCom.model.Panels;
import org.example.projectCom.service.OutputUtils;
import org.example.projectCom.service.errors.NoFreeOutputsException;
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
class PanelGenerationCacheImlTest {

    @Mock
    private PanelsService panelsService;

    @Mock
    private PanelServiceCache cachePanelService;

    @InjectMocks
    private PanelGenerationCacheIml panelGeneration;

    private static final Long PANEL_ID = 10L;
    private static final Long OBJECT_ID = 5L;

    // ---------- помощники ----------

    private PanelDto dto(PanelNameOption type) {
        PanelDto dto = new PanelDto();
        dto.setId(PANEL_ID);
        dto.setObjectId(OBJECT_ID);
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
        @DisplayName("не генератор — исключение, к свободным каналам и БД не обращаемся")
        void rejectsNonGeneratorPanel() {
            when(cachePanelService.getPanelDtoById(PANEL_ID))
                    .thenReturn(dto(PanelNameOption.PANEL_OUTPUT_SWITCHING));

            assertThatThrownBy(() ->
                    panelGeneration.save(PANEL_ID, List.of("OUTPUT_1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("не является панелью генератора");

            verify(cachePanelService, never()).listCheckboxPanelId(any(), any());
            verify(panelsService, never()).getPanelById(any());
            verify(cachePanelService, never()).persist(any(), any(), any());
        }

        @Test
        @DisplayName("выбор пуст и свободных нет — NoFreeOutputsException, БД не трогаем")
        void throwsWhenNothingSelectedAndNothingFree() {
            when(cachePanelService.getPanelDtoById(PANEL_ID))
                    .thenReturn(dto(PanelNameOption.PANEL_GENERATION_1));
            when(cachePanelService.listCheckboxPanelId(OBJECT_ID, PANEL_ID))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> panelGeneration.save(PANEL_ID, List.of()))
                    .isInstanceOf(NoFreeOutputsException.class)
                    .hasMessageContaining("свободных выходов нет");

            verify(panelsService, never()).getPanelById(any());
            verify(cachePanelService, never()).persist(any(), any(), any());
        }

        @Test
        @DisplayName("занятые выходы — assertAllowed бросает, живая сущность не запрашивается")
        void rejectsOccupiedOutputs() {
            when(cachePanelService.getPanelDtoById(PANEL_ID))
                    .thenReturn(dto(PanelNameOption.PANEL_GENERATION_1));
            when(cachePanelService.listCheckboxPanelId(OBJECT_ID, PANEL_ID))
                    .thenReturn(List.of(OutputOption.OUTPUT_1, OutputOption.OUTPUT_2));
            doThrow(new IllegalArgumentException("Выбраны занятые другими панелями выходы"))
                    .when(panelsService)
                    .assertAllowed(any(), any(), any());

            assertThatThrownBy(() ->
                    panelGeneration.save(PANEL_ID, List.of("OUTPUT_1", "OUTPUT_2")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("занятые");

            verify(panelsService, never()).getPanelById(any());
            verify(cachePanelService, never()).persist(any(), any(), any());
        }

        @Test
        @DisplayName("валидный выбор — persist получает живую сущность, кадр генератора и objectId")
        void persistsThroughCacheWithCorrectArguments() {
            when(cachePanelService.getPanelDtoById(PANEL_ID))
                    .thenReturn(dto(PanelNameOption.PANEL_GENERATION_1));
            when(cachePanelService.listCheckboxPanelId(OBJECT_ID, PANEL_ID))
                    .thenReturn(List.of(OutputOption.OUTPUT_1, OutputOption.OUTPUT_2));
            Panels live = livePanel();
            when(panelsService.getPanelById(PANEL_ID)).thenReturn(live);
            when(cachePanelService.persist(any(), any(), any())).thenReturn(live);

            panelGeneration.save(PANEL_ID, List.of("OUTPUT_1", "OUTPUT_2"));

            ArgumentCaptor<Panels> panelCaptor = ArgumentCaptor.forClass(Panels.class);
            ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
            ArgumentCaptor<Long> objectIdCaptor = ArgumentCaptor.forClass(Long.class);
            verify(cachePanelService).persist(panelCaptor.capture(), payloadCaptor.capture(),
                    objectIdCaptor.capture());

            assertThat(panelCaptor.getValue()).isSameAs(live);
            assertThat(objectIdCaptor.getValue()).isEqualTo(OBJECT_ID);

            // round-trip: маски читаются теми же утилитами, что и сохранённый конфиг
            String hex = toHex(payloadCaptor.getValue());
            assertThat(OutputUtils.extractSelectedOutputs(
                    hex, OutputUtils.payloadOffset(PanelNameOption.PANEL_GENERATION_1)))
                    .containsExactlyInAnyOrder(OutputOption.OUTPUT_1, OutputOption.OUTPUT_2);
        }

        @Test
        @DisplayName("пустой выбор, но есть свободные — сохранение проходит")
        void persistsWhenNothingSelectedButFreeOutputsExist() {
            when(cachePanelService.getPanelDtoById(PANEL_ID))
                    .thenReturn(dto(PanelNameOption.PANEL_GENERATION_1));
            when(cachePanelService.listCheckboxPanelId(OBJECT_ID, PANEL_ID))
                    .thenReturn(List.of(OutputOption.OUTPUT_3));
            Panels live = livePanel();
            when(panelsService.getPanelById(PANEL_ID)).thenReturn(live);
            when(cachePanelService.persist(any(), any(), any())).thenReturn(live);

            panelGeneration.save(PANEL_ID, List.of());

            verify(cachePanelService).persist(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("удаление уходит в кэш-сервис, его результат возвращается")
        void deletesThroughCacheService() {
            when(cachePanelService.deletePanel(PANEL_ID)).thenReturn(OBJECT_ID);

            Long objectId = panelGeneration.delete(PANEL_ID);

            assertThat(objectId).isEqualTo(OBJECT_ID);
            verify(cachePanelService).deletePanel(PANEL_ID);
        }
    }

}
