--data.sql
INSERT INTO equipment_object (name) VALUES ('Test object Full');
INSERT INTO panel_config (object_id,name_element,outputs_json)
VALUES (
        1,'PANEL_OUTPUT_SWITCHING',
        '{"hex":"018303030000"}'::jsonb
       );