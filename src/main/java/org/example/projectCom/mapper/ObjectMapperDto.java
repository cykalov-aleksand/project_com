package org.example.projectCom.mapper;

import org.example.projectCom.dto.EquipmentObjectDto;
import org.example.projectCom.model.EquipmentObject;
import org.springframework.stereotype.Component;

@Component
public class ObjectMapperDto {
    public EquipmentObject toEntity(EquipmentObjectDto dto) {
        if (dto == null) {
            return null;
        }
        EquipmentObject object = new EquipmentObject();
        object.setId(dto.getId());
        object.setName(dto.getName());
        return object;
    }

    public EquipmentObjectDto toDto(EquipmentObject object) {
        if (object == null) {
            return null;
        }

        EquipmentObjectDto dto = new EquipmentObjectDto();
        dto.setId(object.getId());
        dto.setName(object.getName());
        return dto;
    }
}

