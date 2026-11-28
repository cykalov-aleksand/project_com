--src/test/resources/schema.sql
DROP  TABLE IF EXISTS panel_config CASCADE;
DROP TABLE IF EXISTS equipment_object CASCADE;

CREATE TABLE equipment_object (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE panel_config (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    object_id BIGINT NOT NULL REFERENCES equipment_object(id) ON DELETE CASCADE,
    name_element VARCHAR(50) NOT NULL,
    selected_port VARCHAR(20),
    outputs_json jsonb,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);