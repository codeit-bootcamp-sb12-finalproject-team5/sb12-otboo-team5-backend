CREATE TABLE recommendation_request_history (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL,
    recommendation_type VARCHAR(20) NOT NULL,
    weather_id          UUID NOT NULL,
    algorithm_version   VARCHAR(100) NOT NULL,
    prompt_version      VARCHAR(100) NOT NULL,
    requested_at        TIMESTAMPTZ(6) NOT NULL,
    created_at          TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT fk_recommendation_request_history_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT ck_recommendation_request_history_type
        CHECK (recommendation_type IN ('OOTD', 'OUTFIT'))
);

CREATE INDEX idx_recommendation_request_history_user_type_requested
    ON recommendation_request_history(user_id, recommendation_type, requested_at DESC);

CREATE TABLE recommendation_request_selected_clothes (
    id                                UUID PRIMARY KEY,
    recommendation_request_history_id UUID NOT NULL,
    clothes_id                        UUID NOT NULL,
    created_at                        TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT fk_recommendation_request_selected_clothes_history
        FOREIGN KEY (recommendation_request_history_id) REFERENCES recommendation_request_history(id),
    CONSTRAINT fk_recommendation_request_selected_clothes_clothes
        FOREIGN KEY (clothes_id) REFERENCES clothes(id),
    CONSTRAINT uq_recommendation_request_selected_clothes
        UNIQUE (recommendation_request_history_id, clothes_id)
);

CREATE TABLE recommendation_outfit_history (
    id                                UUID PRIMARY KEY,
    recommendation_request_history_id UUID NOT NULL,
    rank                              INTEGER NOT NULL,
    outfit_fingerprint                VARCHAR(64) NOT NULL,
    reason                            TEXT NOT NULL,
    exposed_at                        TIMESTAMPTZ(6) NOT NULL,
    created_at                        TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT fk_recommendation_outfit_history_request
        FOREIGN KEY (recommendation_request_history_id) REFERENCES recommendation_request_history(id),
    CONSTRAINT uq_recommendation_outfit_history_request_fingerprint
        UNIQUE (recommendation_request_history_id, outfit_fingerprint)
);

CREATE INDEX idx_recommendation_outfit_history_request
    ON recommendation_outfit_history(recommendation_request_history_id);
CREATE INDEX idx_recommendation_outfit_history_fingerprint
    ON recommendation_outfit_history(outfit_fingerprint);

CREATE TABLE recommendation_outfit_clothes (
    id                                UUID PRIMARY KEY,
    recommendation_outfit_history_id  UUID NOT NULL,
    clothes_id                        UUID NOT NULL,
    created_at                        TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT fk_recommendation_outfit_clothes_history
        FOREIGN KEY (recommendation_outfit_history_id) REFERENCES recommendation_outfit_history(id),
    CONSTRAINT fk_recommendation_outfit_clothes_clothes
        FOREIGN KEY (clothes_id) REFERENCES clothes(id),
    CONSTRAINT uq_recommendation_outfit_clothes
        UNIQUE (recommendation_outfit_history_id, clothes_id)
);

CREATE INDEX idx_recommendation_outfit_clothes_history
    ON recommendation_outfit_clothes(recommendation_outfit_history_id);
