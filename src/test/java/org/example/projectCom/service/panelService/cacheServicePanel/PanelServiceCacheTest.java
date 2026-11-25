package org.example.projectCom.service.panelService.cacheServicePanel;

import org.example.projectCom.dto.PanelDto;
import org.example.projectCom.model.EquipmentObject;
import org.example.projectCom.model.Panels;
import org.example.projectCom.service.panelService.service.PanelsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PanelServiceCacheTest {

    @Mock
    private PanelsService panelsService;

    private PanelServiceCache cacheService;

    private static final Long OBJECT_ID = 5L;
    private static final Long PANEL_ID = 10L;

    @BeforeEach
    void setUp() {
        cacheService = new PanelServiceCache(panelsService);
    }

    private PanelDto dto(Long id, Long objectId) {
        PanelDto dto = new PanelDto();
        dto.setId(id);
        dto.setObjectId(objectId);
        return dto;
    }

    @Nested
    @DisplayName("findPanelsIdObject")
    class FindPanels {

        @Test
        @DisplayName("промах — читает из БД и кладёт в кэш")
        void missesThenCaches() {
            when(panelsService.findPanelsIdObject(OBJECT_ID))
                    .thenReturn(List.of(dto(PANEL_ID, OBJECT_ID)));

            List<PanelDto> first = cacheService.findPanelsIdObject(OBJECT_ID);
            List<PanelDto> second = cacheService.findPanelsIdObject(OBJECT_ID);

            assertThat(second).isSameAs(first);
            // БД запрошена ровно один раз — второй вызов из кэша
            verify(panelsService, times(1)).findPanelsIdObject(OBJECT_ID);
        }

        @Test
        @DisplayName("смена объекта — старый кэш сброшен, новый объект читается из БД")
        void switchesObjectAndEvictsPrevious() {
            Long otherObject = 7L;
            when(panelsService.findPanelsIdObject(OBJECT_ID))
                    .thenReturn(List.of(dto(PANEL_ID, OBJECT_ID)));
            when(panelsService.findPanelsIdObject(otherObject))
                    .thenReturn(List.of(dto(20L, otherObject)));

            cacheService.findPanelsIdObject(OBJECT_ID);
            cacheService.findPanelsIdObject(otherObject);      // переключение
            cacheService.findPanelsIdObject(OBJECT_ID);        // вернулись — кэша нет

            // по 2 обращения к БД: первый визит объекта 5 был из кэша, но после
            // переключения на 7 и обратно кэш объекта 5 сброшен
            verify(panelsService, times(2)).findPanelsIdObject(OBJECT_ID);
            verify(panelsService, times(1)).findPanelsIdObject(otherObject);
        }
    }

    @Nested
    @DisplayName("getPanelDtoById")
    class GetPanelDtoById {

        @Test
        @DisplayName("панель есть в кэше текущего объекта — БД не вызывается")
        void hitsCacheForCurrentObject() {
            when(panelsService.findPanelsIdObject(OBJECT_ID))
                    .thenReturn(List.of(dto(PANEL_ID, OBJECT_ID)));
            cacheService.findPanelsIdObject(OBJECT_ID);        // прогрев кэша

            PanelDto result = cacheService.getPanelDtoById(PANEL_ID);

            assertThat(result.getId()).isEqualTo(PANEL_ID);
            verify(panelsService, never()).getPanelById(any());
        }

        @Test
        @DisplayName("промах — читает из БД, переключает кэш и достаёт панель из списка объекта")
        void missesThenLoadsObjectList() {
            EquipmentObject object = new EquipmentObject();
            object.setId(OBJECT_ID);
            Panels live = new Panels();
            live.setId(PANEL_ID);
            live.setObject(object);          // ← вот это не хватало

            when(panelsService.getPanelById(PANEL_ID)).thenReturn(live);
            when(panelsService.findPanelsIdObject(OBJECT_ID))
                    .thenReturn(List.of(dto(PANEL_ID, OBJECT_ID)));

            PanelDto result = cacheService.getPanelDtoById(PANEL_ID);

            assertThat(result.getId()).isEqualTo(PANEL_ID);
            verify(panelsService).getPanelById(PANEL_ID);
        }

        @Nested
        @DisplayName("запись сбрасывает кэш")
        class Invalidation {

            @Test
            @DisplayName("persist — кэш объекта сброшен, следующий запрос идёт в БД")
            void persistEvictsObjectCache() {
                when(panelsService.findPanelsIdObject(OBJECT_ID))
                        .thenReturn(List.of(dto(PANEL_ID, OBJECT_ID)));
                cacheService.findPanelsIdObject(OBJECT_ID);        // кэш прогрет

                Panels panel = new Panels();
                panel.setId(PANEL_ID);
                when(panelsService.persist(panel, new byte[]{1})).thenReturn(panel);

                cacheService.persist(panel, new byte[]{1}, OBJECT_ID);

                cacheService.findPanelsIdObject(OBJECT_ID);        // должен пойти в БД снова
                verify(panelsService, times(2)).findPanelsIdObject(OBJECT_ID);
            }

            @Test
            @DisplayName("persist — objectId из аргумента, а не из panel.getObject()")
            void persistUsesExplicitObjectId() {
                // панель без setObject — если бы persist читал objectId из ленивой связи,
                // здесь был бы NPE; тест закрепляет контракт "id передаётся явно"
                Panels panel = new Panels();
                panel.setId(PANEL_ID);
                when(panelsService.persist(panel, new byte[]{1})).thenReturn(panel);

                cacheService.persist(panel, new byte[]{1}, OBJECT_ID);

                verify(panelsService).persist(panel, new byte[]{1});
            }
        }
    }
}
