package com.example.projectCom.model;

import com.example.projectCom.setup_controller.PanelNameOption;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "panel_config")
public class Panels {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Внешний ключ на объект
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "object_id", nullable = false)
    private EquipmentObject object;
    @Enumerated(EnumType.STRING)
    @Column(name = "name_element", nullable = false, length = 50)
    private PanelNameOption nameElement;

    @Column(name = "selected_port", length = 20)
    private String selectedPort;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "outputs_json", columnDefinition = "JSONB")
    private String outputsJsonString;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
