package org.example.projectCom.repository;

import org.example.projectCom.model.EquipmentObject;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ObjectRepository extends JpaRepository<EquipmentObject,Long> {
}
