package com.example.projectCom.mapper;


import com.example.projectCom.dto.PanelDto;
import com.example.projectCom.model.Panels;
import org.springframework.stereotype.Component;

@Component
public class PanelsMapperDto {
    public Panels toEntity(PanelDto dto) {
        if (dto == null) {
            return null;
        }
        Panels object = new Panels();
        object.setId(dto.getId());
        object.setNameElement(dto.getNameElement());
        object.setOutputsJsonString(dto.getListCondition());
        return object;
    }

    public PanelDto toDto(Panels object) {
        if (object == null) {
            return null;
        }

        PanelDto dto = new PanelDto();
        dto.setId(object.getId());
        dto.setNameElement(object.getNameElement());
        dto.setListCondition(object.getOutputsJsonString());
        return dto;
    }
}
