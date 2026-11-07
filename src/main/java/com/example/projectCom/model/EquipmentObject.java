package com.example.projectCom.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.Set;

@Entity
@Table(name = "equipment_object")
@Getter
@Setter
@NoArgsConstructor
public class EquipmentObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;
    @UpdateTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    // Связь: один объект → много панелей
    @OneToMany(mappedBy = "object", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<Panels> panels;
}
