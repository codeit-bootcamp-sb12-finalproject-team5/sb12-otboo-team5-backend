CREATE EXTENSION IF NOT EXISTS vector;

-- =========================================================
-- USERS
-- =========================================================

CREATE TABLE IF NOT EXISTS users (
    id                        UUID PRIMARY KEY,
    email                     VARCHAR(255) NOT NULL,
    name                      VARCHAR(50) NOT NULL,
    password                  VARCHAR(60) NOT NULL,
    role                      VARCHAR(20) NOT NULL DEFAULT 'USER',
    locked                    BOOLEAN NOT NULL DEFAULT FALSE,
    token_version             INTEGER NOT NULL DEFAULT 0,
    temp_password             VARCHAR(60),
    temp_password_expires_at  TIMESTAMPTZ(6),
    created_at                TIMESTAMPTZ(6) NOT NULL,
    updated_at                TIMESTAMPTZ(6),

    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'USER'))
);


-- =========================================================
-- REFRESH_TOKENS
-- =========================================================

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    token       VARCHAR(500) NOT NULL,
    expires_at  TIMESTAMPTZ(6) NOT NULL,
    created_at  TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT fk_refresh_tokens_user
    FOREIGN KEY (user_id)
    REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user
    ON refresh_tokens(user_id);


-- =========================================================
-- USER_OAUTH_LINKS
-- =========================================================

CREATE TABLE IF NOT EXISTS user_oauth_links (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL,
    provider     VARCHAR(20) NOT NULL,
    provider_id  VARCHAR(255) NOT NULL,
    created_at   TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT fk_user_oauth_links_user
    FOREIGN KEY (user_id)
    REFERENCES users (id),

    CONSTRAINT uk_user_oauth_provider
    UNIQUE (provider, provider_id),

    CONSTRAINT ck_user_oauth_provider
    CHECK (provider IN ('GOOGLE', 'KAKAO'))
);


-- =========================================================
-- PROFILES
-- =========================================================



-- =========================================================
-- FOLLOWS
-- =========================================================



-- =====================================================
-- WEATHER_GRID
-- =====================================================

CREATE TABLE IF NOT EXISTS weather_grid (
    id UUID NOT NULL PRIMARY KEY,
    nx INTEGER NOT NULL,
    ny INTEGER NOT NULL,
    region_1depth VARCHAR(50),
    region_2depth VARCHAR(50),
    region_3depth VARCHAR(50),
    region_4depth VARCHAR(50),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_requested_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_weather_grid_xy UNIQUE (nx, ny)
);

CREATE INDEX IF NOT EXISTS idx_weather_grid_enabled
    ON weather_grid(enabled, last_requested_at);


-- =====================================================
-- WEATHER_BATCH_EXECUTION
-- =====================================================

CREATE TABLE IF NOT EXISTS weather_batch_execution (
    id UUID NOT NULL PRIMARY KEY,
    job_name VARCHAR(100) NOT NULL,
    target_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_grid_count INTEGER NOT NULL DEFAULT 0,
    success_grid_count INTEGER NOT NULL DEFAULT 0,
    failed_grid_count INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    error_message VARCHAR(1000),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_weather_batch_job_date UNIQUE (job_name, target_date)
);


-- =====================================================
-- WEATHER_OBSERVATION
-- =====================================================

CREATE TABLE IF NOT EXISTS weather_observation (
    id UUID NOT NULL PRIMARY KEY,
    grid_id UUID NOT NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    temperature DECIMAL(6, 2),
    humidity DECIMAL(6, 2),
    precipitation_type VARCHAR(20),
    precipitation_amount DECIMAL(8, 2),
    wind_speed DECIMAL(7, 2),
    wind_direction DECIMAL(7, 2),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_weather_observation_grid
    FOREIGN KEY (grid_id) REFERENCES weather_grid(id),
    CONSTRAINT uk_weather_observation_slot UNIQUE (grid_id, observed_at)
);

CREATE INDEX IF NOT EXISTS idx_weather_observation_lookup
    ON weather_observation(grid_id, observed_at);
CREATE INDEX IF NOT EXISTS idx_weather_observation_retention
    ON weather_observation(observed_at);


-- =====================================================
-- WEATHER_FORECAST
-- =====================================================

CREATE TABLE IF NOT EXISTS weather_forecast (
    id UUID NOT NULL PRIMARY KEY,
    grid_id UUID NOT NULL,
    forecasted_at TIMESTAMP(6) NOT NULL,
    forecast_at TIMESTAMP(6) NOT NULL,
    temperature DECIMAL(6, 2),
    humidity DECIMAL(6, 2),
    precipitation_type VARCHAR(20),
    precipitation_amount DECIMAL(8, 2),
    precipitation_probability DECIMAL(6, 2),
    sky_status VARCHAR(20),
    wind_speed DECIMAL(7, 2),
    wind_direction DECIMAL(7, 2),
    min_temperature DECIMAL(6, 2),
    max_temperature DECIMAL(6, 2),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_weather_forecast_grid
    FOREIGN KEY (grid_id) REFERENCES weather_grid(id),
    CONSTRAINT uk_weather_forecast_slot UNIQUE (grid_id, forecast_at)
);

CREATE INDEX IF NOT EXISTS idx_weather_forecast_lookup
    ON weather_forecast(grid_id, forecast_at);
CREATE INDEX IF NOT EXISTS idx_weather_forecast_issued
    ON weather_forecast(forecasted_at);


-- =====================================================
-- WEATHER_SNAPSHOT
-- =====================================================

CREATE TABLE IF NOT EXISTS weather_snapshot (
    id UUID NOT NULL PRIMARY KEY,
    sky_status VARCHAR(20) NOT NULL,
    precipitation_type VARCHAR(20) NOT NULL,
    precipitation_amount DECIMAL(8, 2) NOT NULL,
    precipitation_probability DECIMAL(6, 2) NOT NULL,
    temperature_current DECIMAL(6, 2) NOT NULL,
    temperature_compared_to_day_before DECIMAL(6, 2),
    temperature_min DECIMAL(6, 2) NOT NULL,
    temperature_max DECIMAL(6, 2) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- =====================================================
-- DM
-- =====================================================

CREATE TABLE IF NOT EXISTS dm_room (
    id UUID PRIMARY KEY,
    dm_key VARCHAR(73) NOT NULL UNIQUE,
    last_message_at TIMESTAMPTZ(6),
    created_at TIMESTAMPTZ(6) NOT NULL,
    deleted_at TIMESTAMPTZ(6)
);

CREATE TABLE IF NOT EXISTS direct_message (
    id UUID PRIMARY KEY,
    dm_room_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT fk_direct_message_dm_room
    FOREIGN KEY (dm_room_id)
    REFERENCES dm_room(id),

    CONSTRAINT fk_direct_message_sender
    FOREIGN KEY (sender_id)
    REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS dm_room_member (
    id UUID PRIMARY KEY,
    dm_room_id UUID NOT NULL,
    user_id UUID NOT NULL,
    last_read_message_id UUID,
    joined_at TIMESTAMPTZ(6) NOT NULL,
    left_at TIMESTAMPTZ(6),

    CONSTRAINT fk_dm_room_member_dm_room
    FOREIGN KEY (dm_room_id)
    REFERENCES dm_room(id),

    CONSTRAINT fk_dm_room_member_user
    FOREIGN KEY (user_id)
    REFERENCES users(id),

    CONSTRAINT fk_dm_room_member_last_read_message
    FOREIGN KEY (last_read_message_id)
    REFERENCES direct_message(id),

    CONSTRAINT uq_dm_room_member_room_user
    UNIQUE (dm_room_id, user_id)
);


-- =========================================================
-- CLOTHES
-- =========================================================

CREATE TABLE clothes (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    is_owned        BOOLEAN NOT NULL,
    preference      INTEGER CHECK (preference >= 1 AND preference <= 5),
    image_url       VARCHAR(1000),
    category        VARCHAR(255),
    gender          VARCHAR(50),
    attribute_text  TEXT,
    attribute_vector VECTOR(512),
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    deleted_at      TIMESTAMP(6) WITH TIME ZONE,

    CONSTRAINT fk_clothes_user
    FOREIGN KEY (user_id)
    REFERENCES users (id)
);


-- =====================================================
-- OUTFIT
-- =====================================================

CREATE TABLE IF NOT EXISTS outfit (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ(6) NOT NULL,
    updated_at TIMESTAMPTZ(6) NOT NULL,
    deleted_at TIMESTAMPTZ(6),

    CONSTRAINT fk_outfit_user
    FOREIGN KEY (user_id)
    REFERENCES users(id)
);


CREATE TABLE IF NOT EXISTS outfit_clothes (
    id UUID PRIMARY KEY,
    outfit_id UUID NOT NULL,
    clothes_id UUID NOT NULL,

    CONSTRAINT fk_outfit_clothes_outfit
    FOREIGN KEY (outfit_id)
    REFERENCES outfit(id),

    CONSTRAINT fk_outfit_clothes_clothes
    FOREIGN KEY (clothes_id)
    REFERENCES clothes(id),

    CONSTRAINT uq_outfit_clothes
    UNIQUE (outfit_id, clothes_id)
);

CREATE TABLE IF NOT EXISTS ootd (
    outfit_id UUID PRIMARY KEY,
    weather_snapshot_id UUID NOT NULL,

    CONSTRAINT fk_ootd_outfit
    FOREIGN KEY (outfit_id)
    REFERENCES outfit(id),

    CONSTRAINT fk_ootd_weather_snapshot
    FOREIGN KEY (weather_snapshot_id)
    REFERENCES weather_snapshot(id)
);


-- =========================================================
-- FEED
-- =========================================================

CREATE TABLE feed (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    content         TEXT,
    is_visible      BOOLEAN NOT NULL,
    like_count      BIGINT NOT NULL DEFAULT 0,
    comment_count   BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    deleted_at      TIMESTAMP(6) WITH TIME ZONE,

    CONSTRAINT fk_feed_outfit
    FOREIGN KEY (id)
    REFERENCES outfit (id),

    CONSTRAINT fk_feed_user
    FOREIGN KEY (user_id)
    REFERENCES users (id)
);

CREATE TABLE comment (
    id          UUID PRIMARY KEY,
    feed_id     UUID NOT NULL,
    user_id     UUID NOT NULL,
    content     VARCHAR(1000) NOT NULL,
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_comment_feed
    FOREIGN KEY (feed_id)
    REFERENCES feed (id),

    CONSTRAINT fk_comment_user
    FOREIGN KEY (user_id)
    REFERENCES users (id)
);

CREATE TABLE feed_like (
    id          UUID PRIMARY KEY,
    feed_id     UUID NOT NULL,
    user_id     UUID NOT NULL,

    CONSTRAINT fk_feed_like_feed
    FOREIGN KEY (feed_id)
    REFERENCES feed (id),

    CONSTRAINT fk_feed_like_user
    FOREIGN KEY (user_id)
    REFERENCES users (id),

    CONSTRAINT uk_feed_like_user_feed
    UNIQUE (feed_id, user_id)
);