package com.example.projectCom.repository;

import com.example.projectCom.model.Panels;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PanelsRepository extends JpaRepository<Panels,Long> {
   @Query("SELECT p FROM Panels p WHERE p.object.id = :objectId")
    List<Panels> findPanels(@Param("objectId") Long objectId);
}
