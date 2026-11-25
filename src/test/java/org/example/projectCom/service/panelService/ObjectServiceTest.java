package org.example.projectCom.service.panelService;

import org.example.projectCom.dto.EquipmentObjectDto;
import org.example.projectCom.mapper.ObjectMapperDto;
import org.example.projectCom.model.EquipmentObject;
import org.example.projectCom.repository.ObjectRepository;
import org.example.projectCom.service.ObjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ObjectServiceTest {

    @Mock
    private ObjectRepository objectRepository;

    @Mock
    private ObjectMapperDto objectMapperDto;

    @InjectMocks
    private ObjectService objectService;

    // ---------- помощники ----------

    private EquipmentObject entity(Long id, String name) {
        EquipmentObject e = new EquipmentObject();
        e.setId(id);
        e.setName(name);
        return e;
    }

    private EquipmentObjectDto dto(Long id, String name) {
        EquipmentObjectDto d = new EquipmentObjectDto();
        d.setId(id);
        d.setName(name);
        return d;
    }

    @Nested
    @DisplayName("findAllObject")
    class FindAll {

        @Test
        void returnsAllMappedToDto() {
            EquipmentObject e1 = entity(1L, "ЯЧЕЙКА-1");
            EquipmentObject e2 = entity(2L, "ЯЧЕЙКА-2");
            when(objectRepository.findAll()).thenReturn(List.of(e1, e2));
            when(objectMapperDto.toDto(e1)).thenReturn(dto(1L, "ЯЧЕЙКА-1"));
            when(objectMapperDto.toDto(e2)).thenReturn(dto(2L, "ЯЧЕЙКА-2"));

            List<EquipmentObjectDto> result = objectService.findAllObject();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(EquipmentObjectDto::getName)
                    .containsExactly("ЯЧЕЙКА-1", "ЯЧЕЙКА-2");
        }

        @Test
        void returnsEmptyListWhenNothingStored() {
            when(objectRepository.findAll()).thenReturn(List.of());

            assertThat(objectService.findAllObject()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        void returnsDtoWhenObjectExists() {
            when(objectRepository.findById(5L)).thenReturn(Optional.of(entity(5L, "ЯЧЕЙКА-5")));
            when(objectMapperDto.toDto(any())).thenReturn(dto(5L, "ЯЧЕЙКА-5"));

            EquipmentObjectDto result = objectService.findById(5L);

            assertThat(result.getId()).isEqualTo(5L);
            assertThat(result.getName()).isEqualTo("ЯЧЕЙКА-5");
        }

        @Test
        void throwsWhenObjectMissing() {
            when(objectRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> objectService.findById(99L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Объект не найден");
        }
    }

    @Nested
    @DisplayName("findByName")
    class FindByName {

        @Test
        void delegatesSearchToRepository() {
            EquipmentObject e = entity(1L, "ЯЧЕЙКА-1");
            when(objectRepository.findObjectByNameContaining("ячейка"))
                    .thenReturn(List.of(e));
            when(objectMapperDto.toDto(e)).thenReturn(dto(1L, "ЯЧЕЙКА-1"));

            List<EquipmentObjectDto> result = objectService.findByName("ячейка");

            assertThat(result).hasSize(1);
            verify(objectRepository).findObjectByNameContaining("ячейка");
        }

        @Test
        void trimsWhitespaceAroundQuery() {
            when(objectRepository.findObjectByNameContaining("ячейка"))
                    .thenReturn(List.of());

            objectService.findByName("   ячейка   ");

            verify(objectRepository).findObjectByNameContaining("ячейка");
        }

        @Test
        void nullBecomesEmptyString() {
            when(objectRepository.findObjectByNameContaining("")).thenReturn(List.of());

            objectService.findByName(null);

            verify(objectRepository).findObjectByNameContaining("");
        }
    }

    @Nested
    @DisplayName("saveObjectDto")
    class Save {

        @Test
        void uppercasesNameBeforeSaving() {
            when(objectMapperDto.toEntity(any())).thenReturn(entity(1L, "ЯЧЕЙКА-1"));
            when(objectRepository.save(any())).thenReturn(entity(1L, "ЯЧЕЙКА-1"));
            when(objectMapperDto.toDto(any())).thenReturn(dto(1L, "ЯЧЕЙКА-1"));

            EquipmentObjectDto result = objectService.saveObjectDto(dto(null, "ячейка-1"));

            ArgumentCaptor<EquipmentObject> captor = ArgumentCaptor.forClass(EquipmentObject.class);
            verify(objectRepository).save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("ЯЧЕЙКА-1");
            assertThat(result.getName()).isEqualTo("ЯЧЕЙКА-1");
        }

        @Test
        void throwsWhenNameIsNull() {
            EquipmentObjectDto input = dto(null, null);

            assertThatThrownBy(() -> objectService.saveObjectDto(input))
                    .isInstanceOf(NullPointerException.class);

            verify(objectRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getNameById")
    class GetName {

        @Test
        void returnsNameWhenObjectExists() {
            when(objectRepository.findById(3L)).thenReturn(Optional.of(entity(3L, "ЯЧЕЙКА-3")));

            assertThat(objectService.getNameById(3L)).isEqualTo("ЯЧЕЙКА-3");
        }

        @Test
        void messageContainsObjectIdWhenMissing() {
            when(objectRepository.findById(42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> objectService.getNameById(42L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("42");
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        void returnsEntityWhenObjectExists() {
            EquipmentObject expected = entity(7L, "ЯЧЕЙКА-7");
            when(objectRepository.findById(7L)).thenReturn(Optional.of(expected));

            assertThat(objectService.getById(7L)).isSameAs(expected);
        }

        @Test
        void throwsWhenObjectMissing() {
            when(objectRepository.findById(8L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> objectService.getById(8L))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        void delegatesToRepository() {
            objectService.delete(11L);

            verify(objectRepository).deleteById(11L);
        }
    }
}
