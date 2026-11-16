package org.example.projectCom.repository;

import org.example.projectCom.model.EquipmentObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ObjectRepository extends JpaRepository<EquipmentObject,Long> {
    @Query(value = "SELECT * FROM equipment_object WHERE LOWER(name) LIKE LOWER(CONCAT('%', :name, '%'))", nativeQuery = true)
    List<EquipmentObject> findObjectByNameContaining(@Param("name") String name);
}
