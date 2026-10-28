package com.example.projectCom.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Set;

@Entity
@Table(name = "equipment_object")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    // Связь: один объект → много панелей
    @OneToMany(mappedBy = "object", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<Panel> panels;
}
