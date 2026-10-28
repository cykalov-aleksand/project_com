package com.example.projectCom.service;

import com.example.projectCom.dto.EquipmentObjectDto;
import com.example.projectCom.mapper.ObjectMapperDto;
import com.example.projectCom.model.EquipmentObject;
import com.example.projectCom.repository.ObjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PanelService {
    private final ObjectRepository objectRepository;
    private final ObjectMapperDto objectMapperDto;
    private final ObjectMapper objectMapper;

       public String mapToJson(Map<String, Boolean> map) throws IOException {
        return objectMapper.writeValueAsString(map);
    }

    public Map<String, Boolean> jsonToMap(String json) throws IOException {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(json, new TypeReference<Map<String, Boolean>>() {});
    }
    public List<EquipmentObjectDto>findAllObject(){
      return objectRepository.findAll().stream().map(objectMapperDto::toDto).toList();
    }
    public EquipmentObjectDto findById(Long id) {
        EquipmentObject entity = objectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Объект не найден"));
        return objectMapperDto.toDto(entity);
    }

    public EquipmentObjectDto saveObjectDto(EquipmentObjectDto dto) {
        EquipmentObject entity = objectMapperDto.toEntity(dto);
        EquipmentObject saved = objectRepository.save(entity);
        return objectMapperDto.toDto(saved);
    }
    public void delete(Long id){
           objectRepository.deleteById(id);
    }
}
