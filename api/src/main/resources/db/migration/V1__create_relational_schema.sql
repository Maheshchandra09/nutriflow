CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(320) NOT NULL UNIQUE,
    role VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES users(id),
    nutritionist_id UUID NOT NULL REFERENCES users(id),
    plan_tier VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    start_date DATE NOT NULL
);

CREATE TABLE nutrition_targets (
    client_id UUID PRIMARY KEY REFERENCES users(id),
    daily_calorie_target INTEGER NOT NULL,
    protein_target_g NUMERIC(8, 2) NOT NULL,
    carb_target_g NUMERIC(8, 2) NOT NULL,
    fat_target_g NUMERIC(8, 2) NOT NULL
);

