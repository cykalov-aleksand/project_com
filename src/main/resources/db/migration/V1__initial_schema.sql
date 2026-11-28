-- 1. Таблица объектов (до 1000 штук)
CREATE TABLE equipment_object (
                                  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                  name        VARCHAR(100) NOT NULL,
                                  description TEXT,
                                  created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 2. Таблица панелей (1–10 панелей на объект)
CREATE TABLE panel_config (
                              id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                              object_id     BIGINT NOT NULL REFERENCES equipment_object(id) ON DELETE CASCADE,
                              name_element  VARCHAR(50) NOT NULL,
                              selected_port VARCHAR(20),
                              outputs_json  JSONB,
                              updated_at    TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 3. Индекс (отдельно, так правильно для PostgreSQL)
CREATE INDEX idx_panel_object_id ON panel_config(object_id);