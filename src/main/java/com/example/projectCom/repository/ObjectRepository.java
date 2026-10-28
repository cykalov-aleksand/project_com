package com.example.projectCom.repository;

import com.example.projectCom.model.EquipmentObject;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObjectRepository extends JpaRepository<EquipmentObject,Long> {
}
