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
    private static final Long OTHER_OBJECT_ID = 7L;
    private static final Long OTHER_PANEL_ID = 20L;

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
    @DisplayName("чтение")
    class Reading {

        @Test
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
            live.setObject(object);

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
            @DisplayName("deletePanel — кэш объекта сброшен, следующий запрос идёт в БД")
            void deleteEvictsObjectCache() {
                when(panelsService.findPanelsIdObject(OBJECT_ID))
                        .thenReturn(List.of(dto(PANEL_ID, OBJECT_ID)));
                cacheService.findPanelsIdObject(OBJECT_ID);            // кэш прогрет

                when(panelsService.deletePanel(PANEL_ID)).thenReturn(OBJECT_ID);

                Long returnedObjectId = cacheService.deletePanel(PANEL_ID);

                assertThat(returnedObjectId).isEqualTo(OBJECT_ID);
                cacheService.findPanelsIdObject(OBJECT_ID);
                verify(panelsService, times(2)).findPanelsIdObject(OBJECT_ID);
            }

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

    @Nested
    @DisplayName("многопользовательский режим")
    class MultiUser {

        @Test
        @DisplayName("кэши разных объектов независимы: переключение не сбрасывает записи")
        void cachesOfDifferentObjectsAreIndependent() {
            when(panelsService.findPanelsIdObject(OBJECT_ID))
                    .thenReturn(List.of(dto(PANEL_ID, OBJECT_ID)));
            when(panelsService.findPanelsIdObject(OTHER_OBJECT_ID))
                    .thenReturn(List.of(dto(OTHER_PANEL_ID, OTHER_OBJECT_ID)));

            cacheService.findPanelsIdObject(OBJECT_ID);
            cacheService.findPanelsIdObject(OTHER_OBJECT_ID);
            cacheService.findPanelsIdObject(OBJECT_ID); // вернулись — кэш ещё жив

            verify(panelsService, times(1)).findPanelsIdObject(OBJECT_ID);
            verify(panelsService, times(1)).findPanelsIdObject(OTHER_OBJECT_ID);
        }

        @Test
        @DisplayName("панель другого закэшированного объекта находится без обращения к getPanelById")
        void findsPanelOfAnotherCachedObject() {
            when(panelsService.findPanelsIdObject(OTHER_OBJECT_ID))
                    .thenReturn(List.of(dto(OTHER_PANEL_ID, OTHER_OBJECT_ID)));
            cacheService.findPanelsIdObject(OTHER_OBJECT_ID);

            PanelDto result = cacheService.getPanelDtoById(OTHER_PANEL_ID);

            assertThat(result.getId()).isEqualTo(OTHER_PANEL_ID);
            verify(panelsService, never()).getPanelById(any());
        }

        @Test
        @DisplayName("persist сбрасывает кэш только своего объекта — чужой остаётся живым")
        void persistEvictsOnlyTargetObject() {
            when(panelsService.findPanelsIdObject(OBJECT_ID))
                    .thenReturn(List.of(dto(PANEL_ID, OBJECT_ID)));
            when(panelsService.findPanelsIdObject(OTHER_OBJECT_ID))
                    .thenReturn(List.of(dto(OTHER_PANEL_ID, OTHER_OBJECT_ID)));
            cacheService.findPanelsIdObject(OBJECT_ID);
            cacheService.findPanelsIdObject(OTHER_OBJECT_ID);

            when(panelsService.persist(any(Panels.class), any(byte[].class)))
                    .thenReturn(new Panels());
            cacheService.persist(new Panels(), new byte[]{1}, OBJECT_ID);

            cacheService.findPanelsIdObject(OBJECT_ID);       // перечитается — 2-й раз
            cacheService.findPanelsIdObject(OTHER_OBJECT_ID); // останется в кэше — 1-й раз

            verify(panelsService, times(2)).findPanelsIdObject(OBJECT_ID);
            verify(panelsService, times(1)).findPanelsIdObject(OTHER_OBJECT_ID);
        }
    }
}
