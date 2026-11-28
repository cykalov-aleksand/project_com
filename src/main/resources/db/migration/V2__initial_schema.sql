CREATE TABLE app_user (
                          id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          username     VARCHAR(50)  NOT NULL UNIQUE,
                          password     VARCHAR(100) NOT NULL,   -- сюда кладём BCrypt-хэш
                          role         VARCHAR(20)  NOT NULL DEFAULT 'USER',
                          created_at   TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- admin / admin123 (хэш сгенерируй заранее, см. ниже)
INSERT INTO app_user (username, password, role)
VALUES ('admin', '$2a$10$erl6nBpYe8R6175NDtJcI..fjIwm4MAPuwo3b7BWR6vS8d3LgXpEa', 'ADMIN');