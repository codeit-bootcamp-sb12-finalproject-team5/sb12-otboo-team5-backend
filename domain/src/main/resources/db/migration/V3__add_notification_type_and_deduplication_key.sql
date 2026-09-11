-- 기존 알림을 보존하면서 유형과 사용자별 업무 중복 방지 키를 추가한다.
ALTER TABLE notification
    ADD COLUMN type VARCHAR(30),
    ADD COLUMN deduplication_key VARCHAR(200);

-- 과거 알림의 유형을 추정하지 않는다. 기존 행마다 고유한 백필 키를 사용한다.
UPDATE notification
SET type = 'LEGACY',
    deduplication_key = 'LEGACY:' || id::text;

ALTER TABLE notification
    ALTER COLUMN type SET NOT NULL,
    ALTER COLUMN deduplication_key SET NOT NULL,
    ADD CONSTRAINT ck_notification_type
        CHECK (type IN (
            'WEATHER_RAIN', 'FEED_CREATED', 'ROLE_CHANGED',
            'FEED_LIKED', 'FEED_COMMENTED', 'FOLLOWED', 'DM_RECEIVED', 'LEGACY'
        )),
    ADD CONSTRAINT uk_notification_receiver_deduplication
        UNIQUE (receiver_id, deduplication_key);
