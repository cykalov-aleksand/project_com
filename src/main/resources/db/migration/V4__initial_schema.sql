CREATE TABLE agent_token (
                             id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                             token      VARCHAR(64)  NOT NULL UNIQUE,
                             username   VARCHAR(50)  NOT NULL,
                             created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                             expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);