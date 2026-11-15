package org.example.projectCom.service;

import org.example.projectCom.dto.EquipmentObjectDto;
import org.example.projectCom.mapper.ObjectMapperDto;
import org.example.projectCom.model.EquipmentObject;
import org.example.projectCom.repository.ObjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ObjectService {
    private final ObjectRepository objectRepository;
    private final ObjectMapperDto objectMapperDto;

    public List<EquipmentObjectDto>findAllObject(){
        return objectRepository.findAll().stream().map(objectMapperDto::toDto).toList();
    }
    public EquipmentObjectDto findById(Long id) {
        EquipmentObject entity = objectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Объект не найден"));
        return objectMapperDto.toDto(entity);
    }
    public List<EquipmentObjectDto> findByName(String name){
        String clean = name == null ? "" : name.trim();
        return objectRepository.findObjectByNameContaining(clean).stream().map(objectMapperDto::toDto).toList();
    }

    public EquipmentObjectDto saveObjectDto(EquipmentObjectDto dto) {
        dto.setName(dto.getName().toUpperCase());
        EquipmentObject entity = objectMapperDto.toEntity(dto);
        EquipmentObject saved = objectRepository.save(entity);
        return objectMapperDto.toDto(saved);
    }
    public String getNameById(Long objectId){
        return objectRepository.findById(objectId)
                .orElseThrow(() -> new RuntimeException("Объект с ID " + objectId + " не найден"))
                .getName();
    }
    public EquipmentObject getById(Long objectId){
        return objectRepository.findById(objectId)
                .orElseThrow(() -> new RuntimeException("Объект не найден"));
    }
    public void delete(Long id){
        objectRepository.deleteById(id);
    }
}
