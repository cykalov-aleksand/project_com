package com.example.projectCom.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.Id;

import java.time.OffsetDateTime;

@Entity
@Table(name = "panel_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Panel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Внешний ключ на объект
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "object_id", nullable = false)
    private EquipmentObject object;

    @Column(name = "name_element", nullable = false, length = 50)
    private String nameElement;

    @Column(name = "selected_port", length = 20)
    private String selectedPort;

    @Column(name = "outputs_json", columnDefinition = "JSONB")
    private String outputsJsonString;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}