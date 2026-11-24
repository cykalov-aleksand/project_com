package org.example.projectCom.repository;

import org.example.projectCom.model.EquipmentObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
public class PanelRepositoryTest {
    @Autowired
    private ObjectRepository objectRepository;
    @Test
    void schemaAndDataAreApplied(){
        List<EquipmentObject> all=objectRepository.findAll();
        assertEquals(1,all.size(),"Ожидаем ровно одну тестовую запись из data.sql");
        EquipmentObject odj=all.getFirst();
        assertEquals("Test object Full",odj.getName());
    }
}
